package com.aidanwhiteley.books.controller;

import com.aidanwhiteley.books.controller.dtos.ClientRoles;
import com.aidanwhiteley.books.controller.exceptions.NotAuthorisedException;
import com.aidanwhiteley.books.controller.jwt.JwtAuthenticationService;
import com.aidanwhiteley.books.controller.jwt.JwtUtils;
import com.aidanwhiteley.books.domain.User;
import com.aidanwhiteley.books.repository.UserRepository;
import com.aidanwhiteley.books.service.UserService;
import com.aidanwhiteley.books.util.JwtAuthenticationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/secure/api")
public class UserController {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

	private final UserRepository userRepository;

	private final JwtAuthenticationUtils authUtils;

	private final JwtAuthenticationService authService;

	private final JwtUtils jwtUtils;

	private final UserService userService;

	@Value("${books.client.postLogonUrl}")
	private String postLogonUrl;

	@Autowired
	public UserController(UserRepository userRepository, JwtAuthenticationUtils jwtAuthenticationUtils,
						  JwtAuthenticationService jwtAuthenticationService, JwtUtils jwtUtils,
						  UserService userService) {
		this.userRepository = userRepository;
		this.authUtils = jwtAuthenticationUtils;
		this.authService = jwtAuthenticationService;
		this.jwtUtils = jwtUtils;
		this.userService = userService;
	}

	@GetMapping(value = "/user")
	public User user(Principal principal, HttpServletResponse response) {

		if (principal == null) {
		    LOGGER.debug("Principal passed to user method was null");
		    throw new NotAuthorisedException("No user data available - if you havent authenticated then this is expected.");
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Principal passed in to user method is: {}", principal);
            }
        }

		Optional<User> user = authUtils.extractUserFromPrincipal(principal, false);
		if (user.isPresent()) {
			return user.get();
		} else {
			// We've been supplied a valid JWT but the user is no longer in the
			// database.
			LOGGER.warn("No user was found for the given principal - assuming an old JWT supplied for a user removed from data store");
			authService.expireJwtCookie(response);
			throw new NotAuthorisedException("No user found in user store for input JWT");
		}
	}

	@GetMapping(value = "/users")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	public List<User> users(Principal principal) {
		return userRepository.findAll();
	}

	@GetMapping(value = "/users/actuator")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	public String getActuatorJwtToken(Principal principal) {
		User user = userService.createOrUpdateActuatorUser();
		return jwtUtils.createTokenForUser(user);
	}

	@DeleteMapping(value = "/users/{id}")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	public ResponseEntity<Object> deleteUserById(@PathVariable("id") String id, Principal principal) {

		Optional<User> user = authUtils.extractUserFromPrincipal(principal, false);
		if (user.isPresent()) {
			if (user.get().getId().equals(id)) {
				LOGGER.warn("User {} on {} attempted to delete themselves. This isn't allowed",
						user.get().getFullName(), user.get().getAuthProvider());
				return ResponseEntity.status(HttpStatus.CONFLICT)
						.body("{\"msg\" : \"Cant delete your own logged on user\"}");
			}

			userRepository.deleteById(id);
			return ResponseEntity.ok().build();
		} else {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
	}

	@PatchMapping(value = "/users/{id}")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	public ResponseEntity<Object> patchUserRolesById(@PathVariable("id") String id, @RequestBody ClientRoles clientRoles,
			Principal principal) {

		Optional<User> user = authUtils.extractUserFromPrincipal(principal, false);
		if (user.isPresent()) {
			if (user.get().getId().equals(id)) {
				LOGGER.warn("User {} on {} attempted to change their own roles. This isn't allowed",
						user.get().getFullName(), user.get().getAuthProvider());
				return ResponseEntity.status(HttpStatus.CONFLICT)
						.body("{\"msg\" : \"Cant change permissions for your own logged on user\"}");
			}

			LOGGER.debug("Received patch of: {}", clientRoles);
			userRepository.updateUserRoles(clientRoles);
			return ResponseEntity.ok().build();
		} else {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
	}

	/**
	 * A custom logout method that removes the necessary client side cookies.
	 *
	 * We dont use the Spring Security config based logout as our needs are
	 * simple and there are complexities with the ordering of Spring Security
	 * filters when we want to be able to call logout when CORS is enabled.
	 */
	@PostMapping(value = "/logout")
	public void logout(HttpServletResponse response) {
		authService.expireJwtCookie(response);
		authService.expireXsrfCookie(response);
		// There shouldnt be any JSessionId cookie - but kill any that exists!
		authService.expireJsessionIdCookie(response);
	}

}
