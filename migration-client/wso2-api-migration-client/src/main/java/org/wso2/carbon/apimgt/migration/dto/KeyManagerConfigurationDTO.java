package org.wso2.carbon.apimgt.migration.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 *KeyManagerConfiguration model
 */
public class KeyManagerConfigurationDTO implements Serializable {


    private static final long serialVersionUID = 1L;

    private String name;
    private String uuid;
    private String displayName;
    private String description;
    private String tenantDomain;
    private Map<String,Object> additionalProperties = new HashMap();
    private String type;
    private boolean enabled;

    public KeyManagerConfigurationDTO() {

    }

    public KeyManagerConfigurationDTO(KeyManagerConfigurationDTO keyManagerConfigurationDTO) {

        this.name = keyManagerConfigurationDTO.getName();
        this.uuid = keyManagerConfigurationDTO.getUuid();
        this.displayName = keyManagerConfigurationDTO.getDisplayName();
        this.description = keyManagerConfigurationDTO.getDescription();
        this.tenantDomain = keyManagerConfigurationDTO.getTenantDomain();
        this.additionalProperties = new HashMap<>(keyManagerConfigurationDTO.getAdditionalProperties());
        this.type = keyManagerConfigurationDTO.getType();
        this.enabled = keyManagerConfigurationDTO.isEnabled();
    }
    public String getName() {

        return name;
    }

    public void setName(String name) {

        this.name = name;
    }

    public String getUuid() {

        return uuid;
    }

    public void setUuid(String uuid) {

        this.uuid = uuid;
    }

    public String getDescription() {

        return description;
    }

    public void setDescription(String description) {

        this.description = description;
    }

    public String getTenantDomain() {

        return tenantDomain;
    }

    public void setTenantDomain(String tenantDomain) {

        this.tenantDomain = tenantDomain;
    }

    public Map<String,Object> getAdditionalProperties() {

        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String,Object> additionalProperties) {

        this.additionalProperties = additionalProperties;
    }

    public String getType() {

        return type;
    }

    public void setType(String type) {

        this.type = type;
    }

    public boolean isEnabled() {

        return enabled;
    }

    public String getDisplayName() {

        return displayName;
    }

    public void setDisplayName(String displayName) {

        this.displayName = displayName;
    }

    public void setEnabled(boolean enabled) {

        this.enabled = enabled;
    }
    public void addProperty(String key,Object value){
        additionalProperties.put(key,value);
    }
    public Object getProperty(String key){
        return additionalProperties.get(key);
    }
    public void removeProperty(String key){
        additionalProperties.remove(key);
    }
}
