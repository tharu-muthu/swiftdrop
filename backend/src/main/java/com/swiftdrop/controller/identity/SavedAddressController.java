package com.swiftdrop.controller.identity;
import com.swiftdrop.dto.identity.*;
import com.swiftdrop.service.identity.SavedAddressService;
import com.swiftdrop.security.IdentityContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/me/addresses")
public class SavedAddressController {
    private final SavedAddressService service;
    public SavedAddressController(SavedAddressService service){this.service=service;}
    @GetMapping public ResponseEntity<List<SavedAddressView>> list(@AuthenticationPrincipal IdentityContext identity){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(identity));}
    @PostMapping public ResponseEntity<SavedAddressView> create(@AuthenticationPrincipal IdentityContext identity,@Valid @RequestBody SavedAddressRequest request){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.create(identity,request));}
    @PutMapping("/{id}") public ResponseEntity<SavedAddressView> update(@AuthenticationPrincipal IdentityContext identity,@PathVariable UUID id,@Valid @RequestBody SavedAddressRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(identity,id,request));}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@AuthenticationPrincipal IdentityContext identity,@PathVariable UUID id,@RequestParam @PositiveOrZero long version){service.delete(identity,id,version);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
}
