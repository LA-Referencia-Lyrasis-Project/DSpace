/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.logic.Filter;
import org.dspace.content.logic.LogicalStatementException;
import org.dspace.content.logic.TrueFilter;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.identifier.dark.DarkArkResponse;
import org.dspace.identifier.dark.DarkBatchResponse;
import org.dspace.identifier.dark.DarkClient;
import org.dspace.identifier.dark.DarkIdentifierException;
import org.dspace.identifier.dark.DarkMetadataBuilder;
import org.dspace.identifier.dark.DarkMetadataRequest;
import org.dspace.identifier.service.DarkService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * IdentifierProvider implementation for dARK persistent identifiers.
 */
public class DarkIdentifierProvider extends FilteredIdentifierProvider {

    private static final Logger log = LogManager.getLogger(DarkIdentifierProvider.class);

    public static final String CFG_ENABLED = "identifier.dark.enabled";
    public static final String CFG_AUTHORITY_ID = "identifier.dark.authority-id";
    public static final String CFG_NAAN = "identifier.dark.naan";
    public static final String CFG_DARK_METADATA = "identifier.dark.metadata";
    public static final String CFG_BATCH_SIZE = "identifier.dark.batch-size";

    public static final String RESERVED = "R";
    public static final String DRAFT = "D";
    public static final String UPDATE = "U";
    public static final String PUBLISHED = "P";
    public static final String TOMBSTONED = "T";

    /** Summary of a refresh of dARKs pending asynchronous Minter processing. */
    public static final class StatusRefreshResult {

        private int checked;
        private int published;
        private int pending;
        private int failed;

        public int getChecked() {
            return checked;
        }

        public int getPublished() {
            return published;
        }

        public int getPending() {
            return pending;
        }

        public int getFailed() {
            return failed;
        }
    }

    public MetadataFieldName darkMetadataFieldName;

    @Autowired(required = true)
    protected DarkService darkService;

    @Autowired(required = true)
    protected DarkClient darkClient;

    @Autowired(required = true)
    protected DarkMetadataBuilder metadataBuilder;

    @Autowired(required = true)
    protected ContentServiceFactory contentServiceFactory;

    @Autowired(required = true)
    protected ItemService itemService;

    protected DarkIdentifierProvider() {
    }

    @PostConstruct
    protected void setDARKMetadata() {
        this.darkMetadataFieldName =
            new MetadataFieldName(this.configurationService.getProperty(CFG_DARK_METADATA, "dc.identifier.dark"));
    }

    @Override
    public boolean supports(Class<? extends Identifier> identifier) {
        return DARK.class.isAssignableFrom(identifier);
    }

    @Override
    public boolean supports(String identifier) {
        try {
            darkService.formatIdentifier(identifier);
        } catch (IdentifierException | IllegalArgumentException ex) {
            return false;
        }
        return true;
    }

    @Override
    public String register(Context context, DSpaceObject dso) throws IdentifierException {
        return register(context, dso, this.filter);
    }

    @Override
    public String register(Context context, DSpaceObject dso, Filter filter) throws IdentifierException {
        if (!isEnabled()) {
            return null;
        }
        if (!(dso instanceof Item)) {
            return null;
        }

        String ark = mint(context, dso, filter);
        register(context, dso, ark, filter);
        return ark;
    }

    @Override
    public void register(Context context, DSpaceObject dso, String identifier) throws IdentifierException {
        register(context, dso, identifier, this.filter);
    }

    @Override
    public void register(Context context, DSpaceObject dso, String identifier, Filter filter)
        throws IdentifierException {
        if (!isEnabled()) {
            return;
        }
        if (!(dso instanceof Item)) {
            return;
        }

        String ark = darkService.formatIdentifier(identifier);
        try {
            DARK dark = loadOrCreateDARK(context, dso, ark, filter);
            if (isTombstoned(dark)) {
                throw new DarkIdentifierException("You tried to register a dARK that is marked as tombstoned.",
                                                  DarkIdentifierException.DARK_IS_TOMBSTONED);
            }

            if (DRAFT.equals(dark.getStatus()) || UPDATE.equals(dark.getStatus())) {
                saveDARKToObject(context, dso, ark);
                return;
            }

            DarkMetadataRequest request = metadataBuilder.build(context, (Item) dso, getAuthorityId(), ark);
            DarkArkResponse response = darkClient.updateMetadata(ark, request);
            applyResponse(dark, response);
            darkService.update(context, dark);
            saveDARKToObject(context, dso, ark);
            refreshStatus(context, dark, ark);
            log.info("Registered dARK {} for Item {} with state {}.", ark, dso.getID(), response.getState());
        } catch (DarkIdentifierException e) {
            log.error("Unable to register dARK {} for Item {}.", ark, dso.getID(), e);
            throw e;
        } catch (SQLException e) {
            log.error("Unable to register dARK {} for Item {}.", ark, dso.getID(), e);
            throw new RuntimeException("Error while registering dARK " + ark + " for item " + dso.getID() + ".", e);
        }
    }

    @Override
    public void reserve(Context context, DSpaceObject dso, String identifier)
        throws IdentifierException, IllegalArgumentException {
        try {
            reserve(context, dso, identifier, this.filter);
        } catch (SQLException e) {
            throw new RuntimeException("Error while reserving dARK for item " + dso.getID() + ".", e);
        }
    }

    @Override
    public void reserve(Context context, DSpaceObject dso, String identifier, Filter filter)
        throws IdentifierException, IllegalArgumentException, SQLException {
        if (!isEnabled()) {
            return;
        }
        if (!(dso instanceof Item)) {
            return;
        }

        String ark = darkService.formatIdentifier(identifier);
        DARK dark = loadOrCreateDARK(context, dso, ark, filter);
        if (dark.getStatus() == null) {
            dark.setStatus(RESERVED);
            darkService.update(context, dark);
        }
        saveDARKToObject(context, dso, ark);
    }

    @Override
    public String mint(Context context, DSpaceObject dso) throws IdentifierException {
        return mint(context, dso, this.filter);
    }

    @Override
    public String mint(Context context, DSpaceObject dso, Filter filter) throws IdentifierException {
        if (!isEnabled()) {
            return null;
        }
        if (!(dso instanceof Item)) {
            return null;
        }

        try {
            String ark = getDARKByObject(context, dso);
            if (ark != null) {
                return ark;
            }

            checkMintable(context, filter, dso);
            DarkArkResponse response = darkClient.reserveARK(getAuthorityId(), getNaan(), dso.getID().toString());
            DARK dark = darkService.create(context);
            dark.setArk(darkService.formatIdentifier(response.getArk()));
            dark.setDSpaceObject(dso);
            dark.setClientItemId(StringUtils.defaultIfBlank(response.getClientItemId(), dso.getID().toString()));
            applyResponse(dark, response);
            if (dark.getStatus() == null) {
                dark.setStatus(RESERVED);
            }
            darkService.update(context, dark);
            log.info("Reserved dARK {} for Item {} with state {}.", dark.getArk(), dso.getID(), response.getState());
            return dark.getArk();
        } catch (DarkIdentifierException e) {
            log.error("Unable to reserve dARK for Item {}.", dso.getID(), e);
            throw e;
        } catch (SQLException e) {
            log.error("Unable to reserve dARK for Item {}.", dso.getID(), e);
            throw new RuntimeException("Error while attempting to create a dARK for item " + dso.getID() + ".", e);
        }
    }

    public Map<UUID, String> reserveBatch(Context context, List<Item> items) throws IdentifierException {
        if (!isEnabled() || items.isEmpty()) {
            return Map.of();
        }

        try {
            String authorityId = getAuthorityId();
            String naan = getNaan();
            List<Item> itemsToReserve = new ArrayList<>();
            List<String> clientItemIds = new ArrayList<>();
            Map<UUID, String> reserved = new LinkedHashMap<>();
            for (Item item : items) {
                String existingArk = getDARKByObject(context, item);
                if (existingArk == null) {
                    itemsToReserve.add(item);
                    clientItemIds.add(item.getID().toString());
                } else {
                    reserved.put(item.getID(), existingArk);
                }
            }
            if (itemsToReserve.isEmpty()) {
                return reserved;
            }

            DarkBatchResponse response = darkClient.reserveARKs(authorityId, naan, clientItemIds);
            Map<String, DarkArkResponse> responseByClientItemId = new LinkedHashMap<>();
            if (response.getResults() != null) {
                for (DarkArkResponse arkResponse : response.getResults()) {
                    if (StringUtils.isNotBlank(arkResponse.getClientItemId())) {
                        responseByClientItemId.put(arkResponse.getClientItemId(), arkResponse);
                    }
                }
            }

            for (Item item : itemsToReserve) {
                DarkArkResponse arkResponse = responseByClientItemId.get(item.getID().toString());
                if (arkResponse == null) {
                    throw new DarkIdentifierException("dARK batch reservation response did not include Item " +
                                                          item.getID() + ".",
                                                      DarkIdentifierException.BAD_ANSWER);
                }

                DARK dark = darkService.create(context);
                dark.setArk(darkService.formatIdentifier(arkResponse.getArk()));
                dark.setDSpaceObject(item);
                dark.setClientItemId(StringUtils.defaultIfBlank(arkResponse.getClientItemId(), item.getID().toString()));
                applyResponse(dark, arkResponse);
                if (dark.getStatus() == null) {
                    dark.setStatus(RESERVED);
                }
                darkService.update(context, dark);
                reserved.put(item.getID(), dark.getArk());
                log.info("Reserved dARK {} for Item {} in batch with state {}.",
                         dark.getArk(), item.getID(), arkResponse.getState());
            }
            return reserved;
        } catch (DarkIdentifierException e) {
            log.error("Unable to reserve dARK batch.", e);
            throw e;
        } catch (SQLException e) {
            log.error("Unable to reserve dARK batch.", e);
            throw new RuntimeException("Error while attempting to reserve dARKs in batch.", e);
        }
    }

    @Override
    public DSpaceObject resolve(Context context, String identifier, String... attributes)
        throws IdentifierNotFoundException, IdentifierNotResolvableException {
        String ark;
        try {
            ark = darkService.formatIdentifier(identifier);
        } catch (IdentifierException e) {
            throw new IdentifierNotResolvableException(e);
        }

        try {
            DARK dark = darkService.findByArk(context, ark);
            if (dark == null || dark.getDSpaceObject() == null) {
                throw new IdentifierNotFoundException();
            }
            return dark.getDSpaceObject();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to retrieve information about a dARK out of database.", e);
        }
    }

    @Override
    public String lookup(Context context, DSpaceObject object)
        throws IdentifierNotFoundException, IdentifierNotResolvableException {
        try {
            String ark = getDARKByObject(context, object);
            if (ark == null) {
                throw new IdentifierNotFoundException("No dARK for DSpaceObject with ID " + object.getID() + ".");
            }
            return ark;
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving dARK out of database.", e);
        }
    }

    @Override
    public void delete(Context context, DSpaceObject dso) throws IdentifierException {
        try {
            String ark = getDARKByObject(context, dso);
            while (ark != null) {
                delete(context, dso, ark);
                ark = getDARKByObject(context, dso);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error while attempting to retrieve dARK information for " + dso.getID() + ".",
                                       e);
        }
    }

    @Override
    public void delete(Context context, DSpaceObject dso, String identifier) throws IdentifierException {
        String ark = darkService.formatIdentifier(identifier);
        try {
            DARK dark = darkService.findByArk(context, ark);
            if (dark != null && !Objects.equals(dso, dark.getDSpaceObject())) {
                throw new DarkIdentifierException("Trying to delete a dARK out of an object it is not assigned to.",
                                                  DarkIdentifierException.MISMATCH);
            }

            removeDARKFromObject(context, dso, ark);
            if (dark != null) {
                dark.setDSpaceObject(null);
                dark.setTombstoneRequested(true);
                darkService.update(context, dark);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error while deleting dARK metadata from item " + dso.getID() + ".", e);
        } catch (AuthorizeException e) {
            throw new DarkIdentifierException("Not authorized to delete dARK metadata.", e,
                                              DarkIdentifierException.UNAUTHORIZED_METADATA_MANIPULATION);
        }
    }

    public void updateMetadata(Context context, DSpaceObject dso, String identifier)
        throws IdentifierException, SQLException {
        String ark = darkService.formatIdentifier(identifier);
        DARK dark = darkService.findByArk(context, ark);
        if (dark == null) {
            throw new DarkIdentifierException("Unable to find dARK.", DarkIdentifierException.DARK_DOES_NOT_EXIST);
        }
        if (!Objects.equals(dark.getDSpaceObject(), dso)) {
            throw new DarkIdentifierException("Cannot update dARK metadata: dARK and DSpaceObject do not match.",
                                              DarkIdentifierException.MISMATCH);
        }
        register(context, dso, ark);
    }

    public void tombstoneOnline(Context context, String identifier) throws IdentifierException, SQLException {
        String ark = darkService.formatIdentifier(identifier);
        DARK dark = darkService.findByArk(context, ark);
        if (dark == null) {
            throw new DarkIdentifierException("Unable to find dARK.", DarkIdentifierException.DARK_DOES_NOT_EXIST);
        }
        if (!dark.isTombstoneRequested()) {
            throw new IllegalArgumentException("Delete the dARK locally before tombstoning it online: " + ark);
        }

        darkClient.tombstoneARK(ark, getAuthorityId());
        dark.setStatus(TOMBSTONED);
        dark.setTombstoneRequested(false);
        darkService.update(context, dark);
    }

    protected DARK loadOrCreateDARK(Context context, DSpaceObject dso, String arkIdentifier, Filter filter)
        throws SQLException, IdentifierException {
        DARK dark = darkService.findByArk(context, arkIdentifier);
        if (dark != null) {
            if (dark.getDSpaceObject() != null && !dso.getID().equals(dark.getDSpaceObject().getID())) {
                throw new DarkIdentifierException("Trying to create a dARK that is already assigned to another object.",
                                                  DarkIdentifierException.DARK_ALREADY_EXISTS);
            }
            checkMintable(context, filter, dso);
        } else {
            checkMintable(context, filter, dso);
            dark = darkService.create(context);
        }

        dark.setArk(arkIdentifier);
        dark.setDSpaceObject(dso);
        dark.setClientItemId(dso.getID().toString());
        darkService.update(context, dark);
        return dark;
    }

    public String getDARKByObject(Context context, DSpaceObject dso) throws SQLException {
        DARK dark = darkService.findDARKByDSpaceObject(context, dso);
        if (dark == null || isTombstoned(dark)) {
            return null;
        }
        if (dark.getArk() == null) {
            throw new IllegalStateException("A dARK with an empty ark column was found for DSO " + dso.getID() + ".");
        }
        return dark.getArk();
    }

    /**
     * Returns the configured Level 1 metadata fields required before registering an Item with dARK.
     *
     * @param item Item to validate
     * @return required metadata fields without a usable value
     */
    public List<String> missingRequiredMetadata(Item item) {
        return metadataBuilder.missingRequiredMetadata(item);
    }

    /**
     * Refreshes local state for all dARKs that are awaiting Minter publication or update.
     *
     * @param context DSpace context
     * @return refresh summary
     * @throws SQLException if pending dARKs cannot be read from the local database
     */
    public StatusRefreshResult refreshPendingStatuses(Context context) throws SQLException {
        StatusRefreshResult result = new StatusRefreshResult();
        for (DARK dark : darkService.findAll(context)) {
            if (!DRAFT.equals(dark.getStatus()) && !UPDATE.equals(dark.getStatus())) {
                continue;
            }

            result.checked++;
            if (StringUtils.isBlank(dark.getArk())) {
                result.failed++;
                log.warn("Cannot refresh a pending dARK with an empty ARK for local record {}.", dark.getID());
                continue;
            }

            try {
                if (!refreshStatus(context, dark, dark.getArk())) {
                    result.failed++;
                } else if (PUBLISHED.equals(dark.getStatus())) {
                    result.published++;
                } else {
                    result.pending++;
                }
            } catch (SQLException | IdentifierException e) {
                result.failed++;
                log.warn("Unable to refresh dARK {} status.", dark.getArk(), e);
            }
        }
        return result;
    }

    protected void saveDARKToObject(Context context, DSpaceObject dso, String ark)
        throws IdentifierException {
        if (!(dso instanceof Item)) {
            return;
        }

        try {
            Item item = (Item) dso;
            String value = darkService.formatIdentifier(ark);
            List<MetadataValue> metadata = itemService.getMetadata(item,
                                                                   darkMetadataFieldName.schema,
                                                                   darkMetadataFieldName.element,
                                                                   darkMetadataFieldName.qualifier,
                                                                   null);
            boolean darkMetadataExists = false;
            for (MetadataValue id : metadata) {
                if (value.equals(id.getValue()) || ark.equals(id.getValue())) {
                    darkMetadataExists = true;
                    break;
                }
            }
            if (!darkMetadataExists) {
                itemService.addMetadata(context, item,
                                        darkMetadataFieldName.schema,
                                        darkMetadataFieldName.element,
                                        darkMetadataFieldName.qualifier,
                                        null,
                                        value);
            }
            if (!darkMetadataExists) {
                itemService.update(context, item);
            }
        } catch (SQLException | AuthorizeException e) {
            throw new DarkIdentifierException("Unable to save dARK metadata.", e);
        }
    }

    protected void removeDARKFromObject(Context context, DSpaceObject dso, String ark)
        throws SQLException, AuthorizeException, IdentifierException {
        if (!(dso instanceof Item)) {
            return;
        }

        Item item = (Item) dso;
        String external = darkService.formatIdentifier(ark);
        List<MetadataValue> metadata = itemService.getMetadata(item,
                                                               darkMetadataFieldName.schema,
                                                               darkMetadataFieldName.element,
                                                               darkMetadataFieldName.qualifier,
                                                               null);
        List<String> remainder = metadata.stream()
            .map(MetadataValue::getValue)
            .filter(value -> !ark.equals(value) && !external.equals(value))
            .toList();

        itemService.clearMetadata(context, item,
                                  darkMetadataFieldName.schema,
                                  darkMetadataFieldName.element,
                                  darkMetadataFieldName.qualifier,
                                  null);
        if (!remainder.isEmpty()) {
            itemService.addMetadata(context, item,
                                    darkMetadataFieldName.schema,
                                    darkMetadataFieldName.element,
                                    darkMetadataFieldName.qualifier,
                                    null,
                                    remainder);
        }
        itemService.update(context, item);
    }

    @Override
    public void checkMintable(Context context, Filter filter, DSpaceObject dso) throws IdentifierException {
        if (filter == null) {
            Filter trueFilter = DSpaceServicesFactory.getInstance().getServiceManager().getServiceByName(
                "always_true_filter", TrueFilter.class);
            filter = this.filter != null ? this.filter : trueFilter;
        }

        if (contentServiceFactory.getDSpaceObjectService(dso).getTypeText(dso).equals("ITEM")) {
            try {
                boolean result = filter.getResult(context, (Item) dso);
                log.debug("Result of dARK filter for {} is {}", dso.getHandle(), result);
                if (!result) {
                    throw new IdentifierNotApplicableException("Item " + dso.getHandle() +
                        " was evaluated as 'false' by the dARK item filter, not minting");
                }
            } catch (LogicalStatementException e) {
                throw new IdentifierNotApplicableException(e);
            }
        }
    }

    @Override
    public void checkMintable(Context context, DSpaceObject dso) throws IdentifierException {
        checkMintable(context, this.filter, dso);
    }

    private String getAuthorityId() throws DarkIdentifierException {
        String authorityId = configurationService.getProperty(CFG_AUTHORITY_ID);
        if (StringUtils.isBlank(authorityId)) {
            throw new DarkIdentifierException("Missing required configuration: " + CFG_AUTHORITY_ID);
        }
        return authorityId;
    }

    private boolean isEnabled() {
        return configurationService.getBooleanProperty(CFG_ENABLED, false);
    }

    private String getNaan() throws DarkIdentifierException {
        String naan = configurationService.getProperty(CFG_NAAN);
        if (StringUtils.isBlank(naan)) {
            throw new DarkIdentifierException("Missing required configuration: " + CFG_NAAN);
        }
        return naan;
    }

    private void applyResponse(DARK dark, DarkArkResponse response) throws IdentifierException {
        if (response == null) {
            return;
        }
        if (StringUtils.isNotBlank(response.getArk())) {
            dark.setArk(darkService.formatIdentifier(response.getArk()));
        }
        String status = statusFromState(response.getState());
        if (status != null) {
            dark.setStatus(status);
        }
        dark.setTarget(response.getTarget());
        dark.setMetadataCid(response.getMetadataCid());
        dark.setLevel1Cid(response.getLevel1Cid());
        dark.setLevel2Cid(response.getLevel2Cid());
        if (StringUtils.isNotBlank(response.getClientItemId())) {
            dark.setClientItemId(response.getClientItemId());
        }
    }

    /**
     * Refreshes the local state after metadata submission without waiting for the asynchronous minter workers.
     */
    private boolean refreshStatus(Context context, DARK dark, String ark) throws SQLException, IdentifierException {
        try {
            DarkArkResponse response = darkClient.getARK(ark);
            applyResponse(dark, response);
            darkService.update(context, dark);
            log.info("Refreshed dARK {} after metadata update; current state is {}.", ark, response.getState());
            return true;
        } catch (DarkIdentifierException e) {
            // The PUT succeeded, so a temporary read failure must not undo Item registration.
            log.warn("dARK {} metadata was submitted, but its current state could not be refreshed.", ark, e);
            return false;
        }
    }

    private String statusFromState(String state) {
        if (RESERVED.equals(state) || DRAFT.equals(state) || UPDATE.equals(state) ||
            PUBLISHED.equals(state) || TOMBSTONED.equals(state)) {
            return state;
        }
        return null;
    }

    private boolean isTombstoned(DARK dark) {
        return TOMBSTONED.equals(dark.getStatus()) || dark.isTombstoneRequested();
    }
}
