package com.swiftdrop.dto.identity;
import com.swiftdrop.entity.identity.SavedAddress;
import java.util.UUID;
public record SavedAddressView(UUID id,String label,String contactName,String contactPhone,String addressLine1,
        String addressLine2,String city,String postalCode,boolean defaultPickup,long version){
    public static SavedAddressView of(SavedAddress a){return new SavedAddressView(a.getId(),a.getLabel(),a.getContactName(),a.getContactPhone(),a.getAddressLine1(),a.getAddressLine2(),a.getCity(),a.getPostalCode(),a.isDefaultPickup(),a.getVersion());}
}
