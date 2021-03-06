package eu.telecomnancy.membershipmanagement.api.services.user;

import eu.telecomnancy.membershipmanagement.api.controllers.utils.cqrs.user.*;
import eu.telecomnancy.membershipmanagement.api.controllers.utils.mappings.UserMapper;
import eu.telecomnancy.membershipmanagement.api.dal.repositories.UserRepository;
import eu.telecomnancy.membershipmanagement.api.domain.Team;
import eu.telecomnancy.membershipmanagement.api.domain.User;
import eu.telecomnancy.membershipmanagement.api.services.MembershipManagementService;
import eu.telecomnancy.membershipmanagement.api.services.exceptions.user.UnknownUserException;
import eu.telecomnancy.membershipmanagement.api.services.exceptions.user.UserAlreadyInATeamException;
import eu.telecomnancy.membershipmanagement.api.services.notification.MessagingService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service to handle {@link User}-related operations
 */
@Log4j2
@Service
public class UserService extends MembershipManagementService implements IUserCommandService, IUserQueryService {

    /**
     * UserDto mapper utility
     */
    protected final UserMapper mapper;

    /**
     * Repository to access the {@link User} entity in the database
     */
    private final UserRepository userRepository;

    /**
     * Create a new instance of the UserService
     *
     * @param messagingService RabbitMQ message dispatcher
     * @param userRepository Repository to access the {@link User} entity in the database
     * @param mapper UserDto mapper utility
     */
    @Autowired
    public UserService(MessagingService messagingService, UserRepository userRepository, UserMapper mapper) {
        super(messagingService);

        this.mapper = mapper;
        this.userRepository = userRepository;
    }

    /**
     * Add a user to a team
     *
     * @param userId Id of the user to add to the team
     * @param team Team that will welcome the new user
     * @return The user newly added
     * @throws UnknownUserException If there is no user for the provided id
     */
    public User addToTeam(long userId, Team team)
            throws UnknownUserException {
        // Retrieve the user
        User user = retrieveUserById(userId);

        // Check if the user can join the team and doesn't not already has one
        if (user.isMemberOfATeam()) {
            log.error("The user {} already has a team and can't join the team {}", user, team);
            throw new UserAlreadyInATeamException(user, team);
        }

        // Perform the addition
        user.setTeam(team);
        return userRepository.save(user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public User createUser(CreateUserCommand createUserCommand) {
        User created = userRepository.save(
                mapper.toUser(createUserCommand));

        log.info("New user created {}", created);

        // Notify other client that the content of the application changed
        messagingService.sendContentUpdatedMessage(createUserCommand);

        return created;
    }

    /**
     * {@inheritDoc}
     */
    public void deleteUser(DeleteUserCommand deleteUserCommand)
            throws UnknownUserException {
        User toDelete = retrieveUserById(deleteUserCommand.getId());

        userRepository.delete(toDelete);

        log.info("User of id {} successfully deleted", toDelete.getId());

        // Notify other client that the content of the application changed
        messagingService.sendContentUpdatedMessage(deleteUserCommand);
    }

    /**
     * Retrieve users depending of their belonging to a team
     *
     * @param hasTeam True if we want to retrieve the users that belong to a team
     *                False if we want the ones that does not belong to a team
     * @return The filter list of users
     */
    private List<User> getUserByHasTeam(boolean hasTeam) {
        return hasTeam
            ? userRepository.findByTeamNotNull()
            : userRepository.findByTeamNull();
    }

    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public User getUser(GetUserQuery getUserQuery) {
        User user = retrieveUserById(getUserQuery.getId());

        log.info("Successfully retrieved user {}", user);

        return user;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<User> getUsers(GetUsersQuery getUsersQuery) {
        Optional<Boolean> hasTeamFilter = getUsersQuery.getHasTeam();

        hasTeamFilter.ifPresent(filterValue
                -> log.info("Retrieving all users such that (user.team != null) = {}",filterValue));

        List<User> users = hasTeamFilter.isEmpty()
            ? userRepository.findAll()
            : getUserByHasTeam(hasTeamFilter.get());

        log.info("Retrieved {} users", users.size());

        return users;
    }

    /**
     * Leave the team of the provided user
     *
     * @param userId Id of the user that will leave the team
     * @throws UnknownUserException If the user does not exists
     */
    public void leaveTeam(long userId)
            throws UnknownUserException {
        User user = retrieveUserById(userId);

        user.setTeam(null);

        userRepository.save(user);

        log.info("The user {} successfully left his team", user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public User patchUser(long userId, PatchUserCommand patchUserCommand)
            throws UnknownUserException {
        // Retrieve the user to update
        User target = retrieveUserById(userId);

        // Perform the update
        log.info("Patch the user {} with {}", target, patchUserCommand);

        mapper.updateFromUser(
                mapper.toUser(patchUserCommand), target);

        log.info("Patched user: {}", target);

        // Notify other client that an operation has been made on the API
        messagingService.sendOperationInfoMessage(patchUserCommand);

        // Return the saved instance
        return userRepository.save(target);
    }

    /**
     * Try to retrieve a user by its id
     *
     * @param userId Id of the user to check
     * @throws UnknownUserException If there is no user for the provided id
     */
    public User retrieveUserById(long userId)
            throws UnknownUserException {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Unknown user of id {}", userId);
                    return new UnknownUserException(userId);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public User updateUser(long userId, UpdateUserCommand updateUserCommand)
            throws UnknownUserException {
        // Retrieve the user to update
        User target = retrieveUserById(userId);

        // Perform the update
        log.info("Update the user {} to {}", target, updateUserCommand);

        mapper.updateFromCommand(updateUserCommand, target);

        log.info("Updated user: {}", target);

        // Notify other client that an operation has been made on the API
        messagingService.sendOperationInfoMessage(updateUserCommand);

        // Return the saved instance
        return userRepository.save(target);
    }

}
