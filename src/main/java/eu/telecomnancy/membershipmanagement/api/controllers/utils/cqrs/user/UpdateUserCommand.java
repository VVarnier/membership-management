package eu.telecomnancy.membershipmanagement.api.controllers.utils.cqrs.user;

import eu.telecomnancy.membershipmanagement.api.domain.User;
import eu.telecomnancy.membershipmanagement.api.services.user.IUserCommandService;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Command to replace a user
 *
 * @see IUserCommandService
 */
@Data
public class UpdateUserCommand {

    /**
     * Age of the user
     */
    @Range(min = User.AGE_MIN, max = User.AGE_MAX)
    private int age;

    /**
     * Firstname of the user
     */
    @NotBlank
    @Size(min = User.NAME_MIN_LENGTH, max = User.NAME_MAX_LENGTH)
    private String firstname;

    /**
     * Name of the user
     */
    @NotBlank
    @Size(min = User.NAME_MIN_LENGTH, max = User.NAME_MAX_LENGTH)
    private String name;

}
