/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.core.ReloadableEntity;

/**
 * dARK identifiers.
 */
@Entity
@Table(name = "dark")
public class DARK implements Identifier, ReloadableEntity<Integer> {

    public static final String SCHEME = "ark:";

    @Id
    @Column(name = "dark_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dark_seq")
    @SequenceGenerator(name = "dark_seq", sequenceName = "dark_seq", allocationSize = 1)
    private Integer id;

    @Column(name = "ark", unique = true, length = 256)
    private String ark;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dspace_object")
    private DSpaceObject dSpaceObject;

    @Column(name = "resource_type_id")
    private Integer resourceTypeId;

    @Column(name = "status")
    private Integer status;

    @Column(name = "client_item_id", length = 128)
    private String clientItemId;

    @Column(name = "target", length = 1024)
    private String target;

    @Column(name = "metadata_cid", length = 128)
    private String metadataCid;

    @Column(name = "level1_cid", length = 128)
    private String level1Cid;

    @Column(name = "level2_cid", length = 128)
    private String level2Cid;

    /**
     * Protected constructor, create object using:
     * {@link org.dspace.identifier.service.DarkService#create(Context)}.
     */
    protected DARK() {
    }

    @Override
    public Integer getID() {
        return id;
    }

    public String getArk() {
        return ark;
    }

    public void setArk(String ark) {
        this.ark = ark;
    }

    public DSpaceObject getDSpaceObject() {
        return dSpaceObject;
    }

    public void setDSpaceObject(DSpaceObject dSpaceObject) {
        this.dSpaceObject = dSpaceObject;
        if (dSpaceObject != null) {
            this.resourceTypeId = dSpaceObject.getType();
        }
    }

    public Integer getResourceTypeId() {
        return resourceTypeId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getClientItemId() {
        return clientItemId;
    }

    public void setClientItemId(String clientItemId) {
        this.clientItemId = clientItemId;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getMetadataCid() {
        return metadataCid;
    }

    public void setMetadataCid(String metadataCid) {
        this.metadataCid = metadataCid;
    }

    public String getLevel1Cid() {
        return level1Cid;
    }

    public void setLevel1Cid(String level1Cid) {
        this.level1Cid = level1Cid;
    }

    public String getLevel2Cid() {
        return level2Cid;
    }

    public void setLevel2Cid(String level2Cid) {
        this.level2Cid = level2Cid;
    }
}
