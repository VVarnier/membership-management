package eu.telecomnancy.membershipmanagement.api.controllers.team;

import eu.telecomnancy.membershipmanagement.api.controllers.utils.cqrs.team.*;
import eu.telecomnancy.membershipmanagement.api.controllers.utils.dto.team.TeamDto;
import eu.telecomnancy.membershipmanagement.api.controllers.utils.dto.user.UserDto;
import eu.telecomnancy.membershipmanagement.api.controllers.utils.mappings.TeamMapper;
import eu.telecomnancy.membershipmanagement.api.controllers.utils.mappings.UserMapper;
import eu.telecomnancy.membershipmanagement.api.domain.Team;
import eu.telecomnancy.membershipmanagement.api.services.team.ITeamCommandService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;

/**
 * API controller for the Team resource
 * Used for write-only operations
 *
 * @see TeamRestController
 */
@RestController
@RequestMapping(
        path = "/api/teams",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Api(value = "Team", tags = { TeamRestController.CONTROLLER_TAG })
public class TeamWriteRestController extends TeamRestController {

    /**
     * Team service used for write-only operation
     */
    private final ITeamCommandService teamService;

    /**
     * Default constructor
     *
     * @param teamService Team service used for write-only operation
     * @param teamMapper TeamDto mapper utility
     * @param userMapper UserDto mapper utility
     */
    @Autowired
    public TeamWriteRestController(ITeamCommandService teamService, TeamMapper teamMapper, UserMapper userMapper) {
        super(teamMapper, userMapper);

        this.teamService = teamService;
    }

    /**
     * Endpoint for: DELETE /teams/:id
     *
     * Delete a team
     *
     * @param id Id of the team to delete
     * @return No Content on success
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a team",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Team successfully deleted"),
                    @ApiResponse(responseCode = "404", description = "Team not found")
            })
    public ResponseEntity<?> delete(
            @ApiParam(value = "Id of the team to delete")
            @PathVariable long id) {
        DeleteTeamCommand deleteTeamCommand = new DeleteTeamCommand(id);

        teamService.deleteTeam(deleteTeamCommand);

        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint for: DELETE /teams/:id/members/:id
     *
     * Remove a user from the team's members
     *
     * @param teamId Id of the team in which the member will be removed
     * @param memberId Id of the user to be removed from the team
     * @return No Content on success
     */
    @DeleteMapping("/{teamId}/members/{memberId}")
    @Operation(summary = "Remove a user from the team's members",
            responses = {
                    @ApiResponse(responseCode = "204", description = "User successfully removed from the team"),
                    @ApiResponse(responseCode = "400", description = "This user does not belong to the team"),
                    @ApiResponse(responseCode = "404", description = "Team not found")
            })
    public ResponseEntity<?> deleteMember(
            @ApiParam(value = "Id of the team in which the member will be removed")
            @PathVariable long teamId,
            @ApiParam(value = "Id of the user to be removed from the team")
            @PathVariable long memberId) {
        DeleteTeamMemberCommand deleteTeamMemberCommand = new DeleteTeamMemberCommand(memberId, teamId);

        teamService.removeMemberFromTeam(deleteTeamMemberCommand);

        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint for: PATCH /teams/:id
     *
     * Update a team, especially its name, while its creation date
     * remains intact
     *
     * We shouldn't update the team's creation date.
     * Therefore, according to the RFC 5789,this endpoint has to be PATCH:
     * The existing HTTP PUT method only allows a complete replacement of a document. This proposal adds a new HTTP
     * method, PATCH, to modify an existing HTTP resource.
     *
     * see: https://tools.ietf.org/html/rfc5789
     *
     * @return The JSON of the updated team as {@link TeamDto}
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Rename an existing team",
            description = """
                The creation date of a team is automatically handled and cannot be updated,
                hence this endpoint being a PATCH and not a PUT
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Team successfully updated"),
                    @ApiResponse(responseCode = "400", description = "Malformed body"),
                    @ApiResponse(responseCode = "404", description = "Team not found")
            })
    public ResponseEntity<TeamDto> patch(
            @ApiParam(value = "Id of the targeted team")
            @PathVariable long id,
            @ApiParam(value = "Payload from which the team details will be replaced")
            @Valid @RequestBody PatchTeamCommand patchTeamCommand) {
        Team team = teamService.patchTeam(id, patchTeamCommand);
        return ResponseEntity.ok(teamMapper.toDto(team));
    }

    /**
     * Endpoint for: POST /teams
     *
     * Create a new team with no member
     *
     * @param createTeamCommand A JSON payload containing the new team's data
     * @return A JSON payload containing all the users
     */
    @PostMapping
    @Operation(summary = "Create a new team with no user",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Team successfully created"),
                    @ApiResponse(responseCode = "400", description = "Malformed body")
            })
    public ResponseEntity<TeamDto> post(
            @ApiParam(value = "Payload from which creating the team")
            @Valid @RequestBody CreateTeamCommand createTeamCommand) {
        // Create the new team and retrieve the newly created one
        Team created = teamService.createTeam(createTeamCommand);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        // Return the result with its location
        return ResponseEntity.created(location)
                .body(teamMapper.toDto(created));
    }

    /**
     * Endpoint for: POST /teams/:id/members
     *
     * Create a new member in the team from an existing user
     *
     * @return The JSON of the updated team as {@link TeamDto}
     */
    @PostMapping("/{id}/members")
    @Operation(summary = "Create a new member in the team from an existing user",
            responses = {
                    @ApiResponse(responseCode = "201", description = "User successfully added"),
                    @ApiResponse(responseCode = "400", description = "Malformed body"),
                    @ApiResponse(responseCode = "404", description = "Team or user not found")
            })
    public ResponseEntity<List<UserDto>> postMember(
            @ApiParam(value = "Id of the team in which the user will be added as a member")
            @PathVariable long id,
            @ApiParam(value = "Payload from which the user will be added as a member")
            @Valid @RequestBody CreateTeamMemberCommand createTeamMemberCommand) {
        // Perform the new member's creation
        Team team = teamService.addTeamMember(id, createTeamMemberCommand);

        // Retrieve the new member created
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}/members")
                .buildAndExpand(team.getId())
                .toUri();

        // Return the result with its location
        return ResponseEntity.created(location)
                .body(userMapper.toDtoList(team.getMembers()));
    }

}
