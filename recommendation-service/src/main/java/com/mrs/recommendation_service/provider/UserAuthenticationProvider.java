package com.mrs.recommendation_service.provider;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserAuthenticationProvider {

    public String getEmail(){
        var userDetails = getUserDetails();

        return userDetails.getUsername();
    }

    public UserDetails getUserDetails() {
        return (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

}
