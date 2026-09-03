/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
    public static final String CFG_PRIMARY_URI_ENABLED = "identifier.dark.primary-uri.enabled";
    public static final String CFG_PRIMARY_URI_METADATA = "identifier.dark.primary-uri.metadata";

    public static final Integer TO_BE_RESERVED = 1;
    public static final Integer RESERVED = 2;
    public static final Integer TO_BE_REGISTERED = 3;
    public static final Integer DRAFT = 4;
    public static final Integer UPDATE = 5;
    public static final Integer PUBLISHED = 6;
    public static final Integer TO_BE_TOMBSTONED = 7;
    public static final Integer TOMBSTONED = 8;
    public static final Integer MINTED = 9;

    public static final String[] statusText = {
        "UNKNOWN",
        "TO_BE_RESERVED",
        "RESERVED",
        "TO_BE_REGISTERED",
        "DRAFT",
        "UPDATE",
        "PUBLISHED",
        "TO_BE_TOMBSTONED",
        "TOMBSTONED",
        "MINTED"
    };

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
        if (dark.getStatus() == null || MINTED.equals(dark.getStatus())) {
            dark.setStatus(TO_BE_RESERVED);
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
                dark.setStatus(TO_BE_TOMBSTONED);
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
        if (!TO_BE_TOMBSTONED.equals(dark.getStatus())) {
            throw new IllegalArgumentException("Delete the dARK locally before tombstoning it online: " + ark);
        }

        darkClient.tombstoneARK(ark, getAuthorityId());
        dark.setStatus(TOMBSTONED);
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
        if (dark.getStatus() == null) {
            dark.setStatus(MINTED);
        }
        darkService.update(context, dark);
        return dark;
    }

    public String getDARKByObject(Context context, DSpaceObject dso) throws SQLException {
        DARK dark = darkService.findDARKByDSpaceObject(context, dso, Arrays.asList(TOMBSTONED, TO_BE_TOMBSTONED));
        if (dark == null) {
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

    protected void saveDARKToObject(Context context, DSpaceObject dso, String ark)
        throws IdentifierException {
        if (!(dso instanceof Item)) {
            return;
        }

        try {
            Item item = (Item) dso;
            String value = darkService.DARKToExternalForm(ark);
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
            if (configurationService.getBooleanProperty(CFG_PRIMARY_URI_ENABLED, false)) {
                MetadataFieldName primaryUriFieldName = new MetadataFieldName(
                    configurationService.getProperty(CFG_PRIMARY_URI_METADATA, "dc.identifier.uri"));
                itemService.clearMetadata(context, item,
                                          primaryUriFieldName.schema,
                                          primaryUriFieldName.element,
                                          primaryUriFieldName.qualifier,
                                          null);
                itemService.addMetadata(context, item,
                                        primaryUriFieldName.schema,
                                        primaryUriFieldName.element,
                                        primaryUriFieldName.qualifier,
                                        null,
                                        value);
            }
            if (!darkMetadataExists || configurationService.getBooleanProperty(CFG_PRIMARY_URI_ENABLED, false)) {
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
        String external = darkService.DARKToExternalForm(ark);
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
        Integer status = statusFromState(response.getState());
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

    private Integer statusFromState(String state) {
        if ("R".equals(state)) {
            return RESERVED;
        }
        if ("D".equals(state)) {
            return DRAFT;
        }
        if ("U".equals(state)) {
            return UPDATE;
        }
        if ("P".equals(state)) {
            return PUBLISHED;
        }
        if ("T".equals(state)) {
            return TOMBSTONED;
        }
        return null;
    }

    private boolean isTombstoned(DARK dark) {
        return TOMBSTONED.equals(dark.getStatus()) || TO_BE_TOMBSTONED.equals(dark.getStatus());
    }
}
