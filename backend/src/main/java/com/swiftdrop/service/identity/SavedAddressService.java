package com.swiftdrop.service.identity;
import com.swiftdrop.dto.identity.*;
import com.swiftdrop.entity.identity.SavedAddress;
import com.swiftdrop.exception.*;
import com.swiftdrop.repository.identity.*;
import com.swiftdrop.security.IdentityContext;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SavedAddressService {
    private final SavedAddressRepository addresses;private final UserRepository users;private final IdentitySessionService sessions;
    public SavedAddressService(SavedAddressRepository addresses,UserRepository users,IdentitySessionService sessions){this.addresses=addresses;this.users=users;this.sessions=sessions;}
    private void check(IdentityContext identity,boolean lock){
        if(lock)users.lockById(identity.userId()).orElseThrow(IdentityAuthenticationException::new);
        sessions.currentIdentity(identity.userId(),identity.sessionId(),identity.authorizationVersion());
    }
    private SavedAddress owned(IdentityContext identity,UUID id){return addresses.findByIdAndUserId(id,identity.userId()).orElseThrow(()->new AccountSettingsException(404,"ADDRESS_NOT_FOUND","Saved address not found."));}
    private void clearDefault(UUID owner,UUID except){
        for(var address:addresses.findByUserIdOrderByDefaultPickupDescCreatedAtAsc(owner))
            if(address.isDefaultPickup()&&!address.getId().equals(except))address.clearDefault();
        addresses.flush();
    }
    @Transactional(readOnly=true) public List<SavedAddressView> list(IdentityContext identity){
        check(identity,false);return addresses.findByUserIdOrderByDefaultPickupDescCreatedAtAsc(identity.userId()).stream().map(SavedAddressView::of).toList();
    }
    @Transactional public SavedAddressView create(IdentityContext identity,SavedAddressRequest request){
        check(identity,true);
        if(addresses.countByUserId(identity.userId())>=20)throw new AccountSettingsException(409,"ADDRESS_LIMIT","You can save up to 20 addresses.");
        if(request.version()!=0)throw new ProfileConflictException();
        if(request.defaultPickup())clearDefault(identity.userId(),null);
        return SavedAddressView.of(addresses.saveAndFlush(new SavedAddress(identity.userId(),request)));
    }
    @Transactional public SavedAddressView update(IdentityContext identity,UUID id,SavedAddressRequest request){
        check(identity,true);var address=owned(identity,id);
        if(address.getVersion()!=request.version())throw new ProfileConflictException();
        if(request.defaultPickup())clearDefault(identity.userId(),id);
        address.update(request);addresses.flush();return SavedAddressView.of(address);
    }
    @Transactional public void delete(IdentityContext identity,UUID id,long version){
        check(identity,true);var address=owned(identity,id);
        if(address.getVersion()!=version)throw new ProfileConflictException();
        addresses.delete(address);addresses.flush();
    }
}
