package eu.telecomnancy.membershipmanagement.api.services.team;

import eu.telecomnancy.membershipmanagement.api.controllers.utils.cqrs.team.*;
import eu.telecomnancy.membershipmanagement.api.controllers.utils.mappings.TeamMapper;
import eu.telecomnancy.membershipmanagement.api.dal.repositories.TeamRepository;
import eu.telecomnancy.membershipmanagement.api.domain.Team;
import eu.telecomnancy.membershipmanagement.api.domain.User;
import eu.telecomnancy.membershipmanagement.api.services.MembershipManagementService;
import eu.telecomnancy.membershipmanagement.api.services.exceptions.team.TeamAlreadyCompleteException;
import eu.telecomnancy.membershipmanagement.api.services.exceptions.team.UnknownTeamException;
import eu.telecomnancy.membershipmanagement.api.services.exceptions.team.UserNotAMemberOfTheTeamException;
import eu.telecomnancy.membershipmanagement.api.services.exceptions.user.UnknownUserException;
import eu.telecomnancy.membershipmanagement.api.services.notification.MessagingService;
import eu.telecomnancy.membershipmanagement.api.services.user.UserService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service to handle {@link Team}-related operations
 */
@Log4j2
@Service
public class TeamService extends MembershipManagementService implements ITeamCommandService, ITeamQueryService {

    /**
     * TeamDto mapper utility
     */
    protected final TeamMapper mapper;

    /**
     * Repository to access the {@link Team} entity in the database
     */
    private final TeamRepository teamRepository;

    /**
     * Injected UserService used to update the membership of the users
     */
    private final UserService userService;

    /**
     * Create a new instance of the TeamService
     *
     * @param messagingService RabbitMQ message dispatcher
     * @param teamRepository Repository to access the {@link Team} entity in the database
     * @param userService Injected UserService used to update the membership of the users
     * @param mapper TeamDto mapper utility
     */
    @Autowired
    public TeamService(MessagingService messagingService, TeamRepository teamRepository, UserService userService,
                       TeamMapper mapper) {
        super(messagingService);

        this.mapper = mapper;
        this.teamRepository = teamRepository;
        this.userService = userService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Team addTeamMember(long teamId, CreateTeamMemberCommand createTeamMemberCommand)
            throws UnknownTeamException, UnknownUserException {
        // Check if the team can have a new member
        Team team = retrieveTeamById(teamId);

        if (team.isComplete()) {
            log.error("The team {} is full and can't have any other member", team);
            throw new TeamAlreadyCompleteException();
        }

        // Add the user to the team members
        User user = userService.addToTeam(
                createTeamMemberCommand.getMemberToAddId(), team);

        // Update the completeness of the team with this new member
        team.setComplete(team.isTeamComplete());
        teamRepository.save(team);

        log.info("User {} successfully added to the members of the team {}", user, team);

        // Notify other client that an operation has been made on the API
        messagingService.sendOperationInfoMessage(createTeamMemberCommand);

        // Return the result
        return team;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Team createTeam(CreateTeamCommand createTeamCommand) {
         Team created = teamRepository.save(
                 mapper.toTeam(createTeamCommand));

         log.info("New team created {}", created);

        // Notify other client that the content of the application changed
        messagingService.sendContentUpdatedMessage(createTeamCommand);

         return created;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteTeam(DeleteTeamCommand deleteTeamCommand)
            throws UnknownTeamException {
        // Retrieve the team to delete
        Team toDelete = retrieveTeamById(
                deleteTeamCommand.getTeamId());

        // Delete the membership of all of its members
        toDelete.getMembers()
                .stream()
                .map(User::getId)
                .forEach(userService::leaveTeam);

        // Clean the team's members
        toDelete.setMembers(null);
        teamRepository.save(toDelete);

        // Perform the deletion
        teamRepository.delete(toDelete);

        // Notify other client that the content of the application changed
        messagingService.sendContentUpdatedMessage(deleteTeamCommand);
        
        log.info("Successfully deleted team {}", toDelete);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeMemberFromTeam(DeleteTeamMemberCommand deleteTeamMemberCommand)
            throws UnknownTeamException, UnknownUserException {
        // Retrieve the team and its members
        long memberId = deleteTeamMemberCommand.getMemberId();
        Team team = retrieveTeamById(deleteTeamMemberCommand.getTeamId());

        // Check if the user does belong to the team
        boolean isUserMemberOfTheTeam = team.getMembers()
                .stream()
                .anyMatch(user -> user.getId() == memberId);

        if (!isUserMemberOfTheTeam) {
            log.error(
                    "Unable to remove the user of id {} from the team {} because he does not belong to is",
                    memberId, team);
            throw new UserNotAMemberOfTheTeamException(memberId, team);
        }

        // Perform the removal
        userService.leaveTeam(memberId);

        // If the team was complete, then it is no longer so
        if (team.isComplete()) {
            team.setComplete(false);
            teamRepository.save(team);
        }

        log.info("The user of id {} has successfully been removed from the team {}", memberId, team);

        // Notify other client that an operation has been made on the API
        messagingService.sendOperationInfoMessage(deleteTeamMemberCommand);
    }

    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public Team getTeam(GetTeamQuery getTeamQuery) {
        Team team = retrieveTeamById(getTeamQuery.getId());

        log.info("Successfully retrieved team {}", team);

        return team;
    }

    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public List<User> getTeamMembers(GetTeamMembersQuery getTeamMembersQuery)
            throws UnknownTeamException {
        Team team = retrieveTeamById(getTeamMembersQuery.getId());
        return team.getMembers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Team> getTeams(GetTeamsQuery getTeamsQuery) {
        Optional<Boolean> isCompleteTeamFilter = getTeamsQuery.getIsComplete();

        isCompleteTeamFilter.ifPresent(filterValue
                -> log.info("Retrieving all teams such that team.isComplete = {}", filterValue));

        List<Team> teams = isCompleteTeamFilter.isPresent()
                ? teamRepository.getTeamByIsComplete(isCompleteTeamFilter.get())
                : teamRepository.findAll();

        log.info("Retrieved {} teams", teams.size());

        return teams;
    }

    /**
     * Try to retrieve a team by its id
     *
     * @param teamId Id of the team to check
     * @throws UnknownTeamException If there is no team for the provided id
     */
    public Team retrieveTeamById(long teamId)
            throws UnknownTeamException {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> {
                    log.error("Unknown team of id {}", teamId);
                    return new UnknownTeamException(teamId);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Team patchTeam(long teamId, PatchTeamCommand patchTeamCommand)
            throws UnknownTeamException {
        // Retrieve the team to update
        Team target = retrieveTeamById(teamId);

        // Perform the update
        log.info("Update the team {} to {}", target, patchTeamCommand);

        mapper.updateFromCommand(patchTeamCommand, target);

        log.info("Updated team: {}", target);

        // Notify other client that an operation has been made on the API
        messagingService.sendOperationInfoMessage(patchTeamCommand);

        // Return the saved instance
        return teamRepository.save(target);
    }

}
