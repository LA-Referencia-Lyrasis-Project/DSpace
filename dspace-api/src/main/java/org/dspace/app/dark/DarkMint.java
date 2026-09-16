/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.dark;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.identifier.DARK;
import org.dspace.identifier.DarkIdentifierProvider;
import org.dspace.identifier.factory.IdentifierServiceFactory;
import org.dspace.identifier.service.IdentifierService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;

/**
 * Mints dARK identifiers or refreshes dARK publication status.
 */
public class DarkMint extends DSpaceRunnable<DarkMintScriptConfiguration> {

    private static final Logger log = LogManager.getLogger(DarkMint.class);
    private static final int DEFAULT_BATCH_SIZE = 100;

    private enum MintResult {
        MINTED,
        ALREADY_ASSIGNED,
        MISSING_METADATA
    }

    private ItemService itemService;
    private IdentifierService identifierService;
    private ConfigurationService configurationService;
    private DarkIdentifierProvider darkIdentifierProvider;

    @Override
    public DarkMintScriptConfiguration getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("dark-mint", DarkMintScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        itemService = ContentServiceFactory.getInstance().getItemService();
        identifierService = IdentifierServiceFactory.getInstance().getIdentifierService();
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        darkIdentifierProvider = identifierService.getProviders().stream()
            .filter(DarkIdentifierProvider.class::isInstance)
            .map(DarkIdentifierProvider.class::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("dARK identifier provider is not configured."));
    }

    @Override
    public void internalRun() throws Exception {
        if (!configurationService.getBooleanProperty(DarkIdentifierProvider.CFG_ENABLED, false)) {
            throw new IllegalStateException("dARK is disabled. Set identifier.dark.enabled = true first.");
        }

        boolean singleItem = commandLine.hasOption("uuid");
        boolean allItems = commandLine.hasOption("all");
        boolean refreshStatus = commandLine.hasOption("refresh-status");
        boolean countLocal = commandLine.hasOption("count-local");
        if ((singleItem ? 1 : 0) + (allItems ? 1 : 0) + (refreshStatus ? 1 : 0) + (countLocal ? 1 : 0) != 1) {
            throw new IllegalArgumentException(
                "Specify exactly one of --uuid <Item UUID>, --all, --refresh-status, or --count-local.");
        }

        if (countLocal) {
            countItemsWithDARK();
            return;
        }
        if (allItems) {
            mintAll();
            return;
        }
        if (refreshStatus) {
            refreshPendingStatuses();
            return;
        }

        Context context = new Context();
        context.turnOffAuthorisationSystem();
        try {
            mintOne(context, UUID.fromString(commandLine.getOptionValue("uuid")));
            context.complete();
        } catch (Exception e) {
            context.abort();
            throw e;
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private void mintOne(Context context, UUID uuid) throws Exception {
        Item item = itemService.find(context, uuid);
        if (item == null) {
            throw new IllegalArgumentException("Item not found: " + uuid);
        }
        mintIfMissing(context, item);
    }

    private void mintAll() throws Exception {
        int minted = 0;
        int alreadyAssigned = 0;
        int missingMetadata = 0;
        int failed = 0;
        int batchSize = configurationService.getIntProperty(DarkIdentifierProvider.CFG_BATCH_SIZE, DEFAULT_BATCH_SIZE);
        if (batchSize < 1) {
            batchSize = DEFAULT_BATCH_SIZE;
        }

        List<UUID> itemIds = findItemIdsWithoutDARK();
        int batchCount = numberOfBatches(itemIds.size(), batchSize);
        for (int start = 0; start < itemIds.size(); start += batchSize) {
            int end = Math.min(start + batchSize, itemIds.size());
            int batchNumber = (start / batchSize) + 1;
            MintSummary summary = mintBatch(itemIds.subList(start, end), batchNumber, batchCount);
            minted += summary.minted;
            alreadyAssigned += summary.alreadyAssigned;
            missingMetadata += summary.missingMetadata;
            failed += summary.failed;
        }
        handler.logInfo(String.format("dARK mint completed: %d minted, %d already assigned, " +
                                      "%d skipped for missing metadata, %d failed.",
                                      minted, alreadyAssigned, missingMetadata, failed));
        if (failed > 0) {
            throw new IllegalStateException("dARK mint completed with " + failed + " failures.");
        }
    }

    /**
     * Materialize only Item UUIDs without a local dARK association before starting writes.
     */
    private List<UUID> findItemIdsWithoutDARK() throws Exception {
        Context context = new Context();
        context.turnOffAuthorisationSystem();
        try {
            List<UUID> itemIds = darkIdentifierProvider.findItemIdsWithoutDARK(context);
            context.complete();
            return itemIds;
        } catch (Exception e) {
            context.abort();
            throw e;
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private void countItemsWithDARK() throws Exception {
        Context context = new Context();
        context.turnOffAuthorisationSystem();
        try {
            long count = darkIdentifierProvider.countItemsWithDARK(context);
            context.complete();
            handler.logInfo("Items with a local dARK association: " + count + ".");
        } catch (Exception e) {
            context.abort();
            throw e;
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private MintSummary mintBatch(List<UUID> itemIds, int batchNumber, int batchCount) {
        MintSummary summary = new MintSummary();
        List<Item> itemsToMint = new ArrayList<>();
        int registered = 0;
        Context context = new Context();
        context.turnOffAuthorisationSystem();
        try {
            for (UUID itemId : itemIds) {
                Item item = itemService.find(context, itemId);
                if (item == null) {
                    summary.failed++;
                    log.warn("Item {} no longer exists and was skipped during dARK minting.", itemId);
                    continue;
                }

                try {
                    MintResult result = checkMintable(context, item);
                    if (MintResult.ALREADY_ASSIGNED.equals(result)) {
                        summary.alreadyAssigned++;
                    } else if (MintResult.MISSING_METADATA.equals(result)) {
                        summary.missingMetadata++;
                    } else {
                        itemsToMint.add(item);
                    }
                } catch (Exception e) {
                    summary.failed++;
                    log.error("Unable to mint dARK for Item {}.", item.getID(), e);
                }
            }

            if (!itemsToMint.isEmpty()) {
                handler.logInfo(String.format("Minting dARK batch %d/%d (%d ARKs).",
                                              batchNumber, batchCount, itemsToMint.size()));
                registered = registerBatch(context, itemsToMint);
            }
            context.complete();
            summary.minted += registered;
            for (Item item : itemsToMint) {
                handler.logInfo("Minted dARK for Item " + item.getID() + ".");
            }
        } catch (Exception e) {
            summary.failed += itemsToMint.size();
            log.error("Unable to process dARK batch.", e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
        }
        return summary;
    }

    private static class MintSummary {
        private int minted;
        private int alreadyAssigned;
        private int missingMetadata;
        private int failed;
    }

    private int registerBatch(Context context, List<Item> batch) throws Exception {
        Map<UUID, String> arks = darkIdentifierProvider.reserveBatch(context, batch);
        for (Item item : batch) {
            String ark = arks.get(item.getID());
            if (StringUtils.isBlank(ark)) {
                throw new IllegalStateException("dARK batch reservation returned no ARK for Item " + item.getID());
            }
            identifierService.register(context, item, ark);
        }
        return batch.size();
    }

    private MintResult mintIfMissing(Context context, Item item) throws Exception {
        MintResult result = checkMintable(context, item);
        if (!MintResult.MINTED.equals(result)) {
            return result;
        }

        identifierService.register(context, item, DARK.class);
        handler.logInfo("Minted dARK for Item " + item.getID() + ".");
        return MintResult.MINTED;
    }

    private MintResult checkMintable(Context context, Item item) throws Exception {
        String ark = identifierService.lookup(context, item, DARK.class);
        if (StringUtils.isNotBlank(ark)) {
            handler.logInfo("Item " + item.getID() + " already has dARK " + ark + ".");
            return MintResult.ALREADY_ASSIGNED;
        }

        darkIdentifierProvider.checkMintable(context, item);
        List<String> missingMetadata = darkIdentifierProvider.missingRequiredMetadata(item);
        if (!missingMetadata.isEmpty()) {
            handler.logInfo("Item " + item.getID() + " skipped: missing required dARK metadata " +
                            String.join(", ", missingMetadata) + ".");
            return MintResult.MISSING_METADATA;
        }

        return MintResult.MINTED;
    }

    private void refreshPendingStatuses() throws Exception {
        List<String> pendingArks = findPendingARKs();

        int batchSize = configurationService.getIntProperty(DarkIdentifierProvider.CFG_BATCH_SIZE, DEFAULT_BATCH_SIZE);
        if (batchSize < 1) {
            batchSize = DEFAULT_BATCH_SIZE;
        }

        int checked = 0;
        int published = 0;
        int pending = 0;
        int failed = 0;
        int batchCount = numberOfBatches(pendingArks.size(), batchSize);
        for (int start = 0; start < pendingArks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, pendingArks.size());
            int batchNumber = (start / batchSize) + 1;
            handler.logInfo(String.format("Refreshing dARK status batch %d/%d (%d ARKs).",
                                          batchNumber, batchCount, end - start));
            StatusRefreshSummary summary = refreshStatusBatch(pendingArks.subList(start, end));
            checked += summary.checked;
            published += summary.published;
            pending += summary.pending;
            failed += summary.failed;
        }
        handler.logInfo(String.format("dARK status refresh completed: %d checked, %d published, %d pending, %d failed.",
                                      checked, published, pending, failed));
        if (failed > 0) {
            throw new IllegalStateException("dARK status refresh completed with " + failed + " failures.");
        }
    }

    private List<String> findPendingARKs() throws Exception {
        Context context = new Context();
        context.turnOffAuthorisationSystem();
        try {
            List<String> arks = darkIdentifierProvider.findPendingARKs(context);
            context.complete();
            return arks;
        } catch (Exception e) {
            context.abort();
            throw e;
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private StatusRefreshSummary refreshStatusBatch(List<String> arks) {
        StatusRefreshSummary summary = new StatusRefreshSummary();
        Context context = new Context();
        context.turnOffAuthorisationSystem();
        try {
            DarkIdentifierProvider.StatusRefreshResult result =
                darkIdentifierProvider.refreshPendingStatuses(context, arks);
            context.complete();
            summary.checked = result.getChecked();
            summary.published = result.getPublished();
            summary.pending = result.getPending();
            summary.failed = result.getFailed();
        } catch (Exception e) {
            summary.failed = arks.size();
            log.error("Unable to refresh dARK status batch.", e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
        }
        return summary;
    }

    private int numberOfBatches(int size, int batchSize) {
        return (size + batchSize - 1) / batchSize;
    }

    private static class StatusRefreshSummary {
        private int checked;
        private int published;
        private int pending;
        private int failed;
    }
}
