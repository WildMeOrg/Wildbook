package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Project.doesUserOwnProject: the caller must be the stored owner and still a project user.
 */
class ProjectOwnershipTest {
    private Shepherd shepherd;
    private HttpServletRequest request;
    private User owner;
    private User other;

    @BeforeEach void setUp() {
        shepherd = mock(Shepherd.class);
        request = mock(HttpServletRequest.class);
        owner = mock(User.class);
        when(owner.getId()).thenReturn("owner-uuid");
        other = mock(User.class);
        when(other.getId()).thenReturn("other-uuid");
    }

    private void caller(User user) {
        when(shepherd.getUser(request)).thenReturn(user);
    }

    @Test void ownerOwns() {
        Project project = new Project("PRJ");
        project.setOwner(new User("owner-uuid"));
        caller(new User("owner-uuid"));
        assertTrue(project.doesUserOwnProject(shepherd, request));
    }

    @Test void aMalformedMemberDoesNotBreakTheCheck() {
        Project project = new Project("PRJ");
        project.setOwner(owner);
        project.getUsers().add(0, null);
        caller(owner);
        assertTrue(project.doesUserOwnProject(shepherd, request));
    }

    @Test void otherUserDoesNotOwn() {
        Project project = new Project("PRJ");
        project.setOwner(owner);
        project.addUser(other);
        caller(other);
        assertFalse(project.doesUserOwnProject(shepherd, request));
    }

    @Test void nobodyOwnsAProjectWithoutAnOwner() {
        Project project = new Project("PRJ");
        project.addUser(other);
        caller(other);
        assertFalse(project.doesUserOwnProject(shepherd, request));
    }

    @Test void nobodyOwnsAProjectWithoutUsers() {
        caller(owner);
        assertFalse(new Project("PRJ").doesUserOwnProject(shepherd, request));
    }

    @Test void ownerRemovedFromTheProjectNoLongerOwns() {
        Project project = new Project("PRJ");
        project.setOwner(owner);
        project.removeUser(owner);
        caller(owner);
        assertFalse(project.doesUserOwnProject(shepherd, request));
    }

    @Test void loggedOutCallerDoesNotOwn() {
        Project project = new Project("PRJ");
        project.setOwner(owner);
        caller(null);
        assertFalse(project.doesUserOwnProject(shepherd, request));
    }
}
