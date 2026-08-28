package in.retailflow.api.tenant.domain;

import in.retailflow.api.common.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "tenants")
@Filter(name = "tenantFilter", condition = "id = :tenantId")
public class Tenant extends AuditedEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(length = 15)
    private String gstin;

    @Column(length = 20)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;
    private String state;
    private String pincode;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 64)
    private BusinessType businessType = BusinessType.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_mode", nullable = false, length = 32)
    private SalesMode salesMode = SalesMode.HYBRID;

    protected Tenant() {}

    public Tenant(UUID id, String name) {
        this.id = id;
        this.name = name;
        this.businessType = BusinessType.OTHER;
        this.salesMode = SalesMode.HYBRID;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTimezone() {
        return timezone;
    }

    public BusinessType getBusinessType() {
        return businessType;
    }

    public void setBusinessType(BusinessType businessType) {
        this.businessType = businessType;
    }

    public SalesMode getSalesMode() {
        return salesMode;
    }

    public void setSalesMode(SalesMode salesMode) {
        this.salesMode = salesMode;
    }
}
