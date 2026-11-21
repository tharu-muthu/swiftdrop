package com.swiftdrop.entity.identity;
import com.swiftdrop.dto.identity.SavedAddressRequest;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="saved_addresses",schema="swiftdrop")
public class SavedAddress extends CreatedEntity {
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(nullable=false,length=60) private String label;
    @Column(name="contact_name",nullable=false,length=200) private String contactName;
    @Column(name="contact_phone",nullable=false,length=32) private String contactPhone;
    @Column(name="address_line1",nullable=false,length=140) private String addressLine1;
    @Column(name="address_line2",nullable=false,length=140) private String addressLine2;
    @Column(nullable=false,length=100) private String city;
    @Column(name="postal_code",nullable=false,length=5) private String postalCode;
    @Column(name="default_pickup",nullable=false) private boolean defaultPickup;
    @Version @Column(nullable=false) private long version;
    protected SavedAddress() { }
    public SavedAddress(UUID owner,SavedAddressRequest request){userId=owner;update(request);}
    public void update(SavedAddressRequest r){label=r.label().strip();contactName=r.contactName().strip();contactPhone=r.contactPhone();addressLine1=r.addressLine1().strip();addressLine2=r.addressLine2().strip();city=r.city().strip();postalCode=r.postalCode();defaultPickup=r.defaultPickup();}
    public void clearDefault(){defaultPickup=false;}
    public UUID getUserId(){return userId;} public String getLabel(){return label;}
    public String getContactName(){return contactName;} public String getContactPhone(){return contactPhone;}
    public String getAddressLine1(){return addressLine1;} public String getAddressLine2(){return addressLine2;}
    public String getCity(){return city;} public String getPostalCode(){return postalCode;}
    public boolean isDefaultPickup(){return defaultPickup;} public long getVersion(){return version;}
}
