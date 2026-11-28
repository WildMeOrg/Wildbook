package org.ecocean.servlet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.Encounter;
import org.ecocean.Project;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/**
 * ProjectDelete is for the project owner or an admin only.
 */
class ProjectDeletePermissionTest {
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Project project;

    @BeforeEach void setUp() throws Exception {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(
            "{\"projectIdPrefix\":\"PRJ\"}")));
        project = mock(Project.class);
        when(project.getEncounters()).thenReturn(new ArrayList<Encounter>());
    }

    private MockedConstruction<Shepherd> shepherds() {
        return mockConstruction(Shepherd.class, (mock, ctx) -> {
            when(mock.getProjectByProjectIdPrefix("PRJ")).thenReturn(project);
        });
    }

    private void caller(boolean owner, boolean admin) {
        when(project.doesUserOwnProject(any(Shepherd.class), any(HttpServletRequest.class)))
            .thenReturn(owner);
        when(request.isUserInRole("admin")).thenReturn(admin);
    }

    @Test void refusesSomeoneWhoIsNotOwnerOrAdmin() throws Exception {
        caller(false, false);
        try (MockedConstruction<Shepherd> sh = shepherds()) {
            new ProjectDelete().doPost(request, response);
            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            verify(project, never()).getEncounters();
            verify(project, never()).clearAllEncounters();
            verify(sh.constructed().get(0), never()).updateDBTransaction();
            verify(sh.constructed().get(0), never()).throwAwayProject(any(Project.class));
        }
    }

    @Test void deletesForTheOwner() throws Exception {
        caller(true, false);
        try (MockedConstruction<Shepherd> sh = shepherds()) {
            new ProjectDelete().doPost(request, response);
            verify(response).setStatus(HttpServletResponse.SC_OK);
            verify(sh.constructed().get(0)).throwAwayProject(project);
        }
    }

    @Test void deletesForAnAdminWhoIsNotTheOwner() throws Exception {
        caller(false, true);
        try (MockedConstruction<Shepherd> sh = shepherds()) {
            new ProjectDelete().doPost(request, response);
            verify(response).setStatus(HttpServletResponse.SC_OK);
            verify(sh.constructed().get(0)).throwAwayProject(project);
        }
    }
}
