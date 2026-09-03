/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.dark;

import java.util.Iterator;
import java.util.List;
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
 * Mints dARK identifiers for one Item or for all archived Items without a dARK.
 */
public class DarkMint extends DSpaceRunnable<DarkMintScriptConfiguration> {

    private static final Logger log = LogManager.getLogger(DarkMint.class);

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
        if (singleItem == allItems) {
            throw new IllegalArgumentException("Specify exactly one of --uuid <Item UUID> or --all.");
        }

        Context context = new Context();
        context.turnOffAuthorisationSystem();
        try {
            if (singleItem) {
                mintOne(context, UUID.fromString(commandLine.getOptionValue("uuid")));
            } else {
                mintAll(context);
            }
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

    private void mintAll(Context context) throws Exception {
        int minted = 0;
        int alreadyAssigned = 0;
        int missingMetadata = 0;
        int failed = 0;
        Iterator<Item> items = itemService.findAll(context);
        while (items.hasNext()) {
            Item item = items.next();
            try {
                switch (mintIfMissing(context, item)) {
                    case MINTED:
                        minted++;
                        break;
                    case ALREADY_ASSIGNED:
                        alreadyAssigned++;
                        break;
                    case MISSING_METADATA:
                        missingMetadata++;
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                failed++;
                log.error("Unable to mint dARK for Item {}.", item.getID(), e);
            }
        }
        handler.logInfo(String.format("dARK mint completed: %d minted, %d already assigned, " +
                                      "%d skipped for missing metadata, %d failed.",
                                      minted, alreadyAssigned, missingMetadata, failed));
        if (failed > 0) {
            throw new IllegalStateException("dARK mint completed with " + failed + " failures.");
        }
    }

    private MintResult mintIfMissing(Context context, Item item) throws Exception {
        String ark = identifierService.lookup(context, item, DARK.class);
        if (StringUtils.isNotBlank(ark)) {
            handler.logInfo("Item " + item.getID() + " already has dARK " + ark + ".");
            return MintResult.ALREADY_ASSIGNED;
        }

        List<String> missingMetadata = darkIdentifierProvider.missingRequiredMetadata(item);
        if (!missingMetadata.isEmpty()) {
            handler.logInfo("Item " + item.getID() + " skipped: missing required dARK metadata " +
                            String.join(", ", missingMetadata) + ".");
            return MintResult.MISSING_METADATA;
        }

        identifierService.register(context, item, DARK.class);
        handler.logInfo("Minted dARK for Item " + item.getID() + ".");
        return MintResult.MINTED;
    }
}