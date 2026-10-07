package com.resqroute;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String citizenToken;
    private static String adminToken;
    private static String trafficToken;

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = mapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("token").asText();
    }

    @Test
    @Order(1)
    void publicReadRequiresNoTokenButWritesDo() throws Exception {
        mvc.perform(get("/api/network/roads")).andExpect(status().isOk());
        mvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ACCIDENT\",\"severity\":\"LOW\",\"locationId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void loginIssuesJwtForSeededAccounts() throws Exception {
        citizenToken = login("citizen", "citizen123");
        adminToken = login("admin", "admin123");
        trafficToken = login("traffic", "traffic123");
        assertNotNull(citizenToken);
        assertNotEquals(citizenToken, adminToken);
    }

    @Test
    @Order(3)
    void registrationCreatesUserRoleOnly() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newbie\",\"email\":\"newbie@x.io\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));

        String token = login("newbie", "secret1");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @Order(4)
    void badPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(5)
    void routeComputationReturnsAllThreeOptions() throws Exception {
        MvcResult result = mvc.perform(post("/api/routes/compute")
                        .header("Authorization", "Bearer " + citizenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":1,\"destinationId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routes.length()").value(3))
                .andReturn();

        JsonNode routes = mapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("routes");
        for (JsonNode route : routes) {
            assertTrue(route.get("found").asBoolean(), "seeded city must be fully connected");
            assertTrue(route.get("distanceKm").asDouble() > 0);
            assertFalse(route.get("nodeNames").isEmpty());
        }
    }

    @Test
    @Order(6)
    void citizenCannotReachAdminApi() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void incidentReportDegradesRoadAndResolveRestoresIt() throws Exception {
        MvcResult before = mvc.perform(get("/api/network/roads"))
                .andExpect(status().isOk())
                .andReturn();
        long roadId = mapper.readTree(before.getResponse().getContentAsString())
                .get("data").get("roads").get(0).get("id").asLong();

        MvcResult incidentResult = mvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + citizenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ACCIDENT\",\"severity\":\"CRITICAL\",\"locationId\":2," +
                                "\"affectedRoadId\":" + roadId + ",\"description\":\"pile up\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        long incidentId = mapper.readTree(incidentResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        MvcResult afterReport = mvc.perform(get("/api/network/roads"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode roads = mapper.readTree(afterReport.getResponse().getContentAsString()).get("data").get("roads");
        JsonNode affected = null;
        for (JsonNode road : roads) {
            if (road.get("id").asLong() == roadId) {
                affected = road;
            }
        }
        assertNotNull(affected);
        assertEquals("BLOCKED", affected.get("status").asText());

        mvc.perform(post("/api/incidents/" + incidentId + "/resolve")
                        .header("Authorization", "Bearer " + trafficToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        MvcResult afterResolve = mvc.perform(get("/api/network/roads"))
                .andExpect(status().isOk())
                .andReturn();
        roads = mapper.readTree(afterResolve.getResponse().getContentAsString()).get("data").get("roads");
        for (JsonNode road : roads) {
            if (road.get("id").asLong() == roadId) {
                assertEquals("OPEN", road.get("status").asText());
                assertEquals("FREE_FLOW", road.get("trafficLevel").asText());
            }
        }
    }

    @Test
    @Order(8)
    void emergencyDispatchPicksFastestEligibleUnit() throws Exception {
        MvcResult incidentResult = mvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + citizenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FIRE\",\"severity\":\"HIGH\",\"locationId\":6}"))
                .andExpect(status().isOk())
                .andReturn();
        long incidentId = mapper.readTree(incidentResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        MvcResult etas = mvc.perform(get("/api/emergency/etas?incidentId=" + incidentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode etaList = mapper.readTree(etas.getResponse().getContentAsString()).get("data");
        assertTrue(etaList.size() >= 1);

        MvcResult dispatch = mvc.perform(post("/api/emergency/dispatch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incidentId\":" + incidentId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.etaMinutes").isNumber())
                .andReturn();
        JsonNode dispatchData = mapper.readTree(dispatch.getResponse().getContentAsString()).get("data");
        assertEquals(incidentId, dispatchData.get("incidentId").asLong());
        assertFalse(dispatchData.get("routeNodes").isEmpty());

        mvc.perform(get("/api/emergency/units").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units[?(@.status=='BUSY')]").
                        exists());
    }

    @Test
    @Order(9)
    void hospitalRecommendationRanksByEta() throws Exception {
        MvcResult result = mvc.perform(get("/api/hospitals/recommend?fromNodeId=1&minBeds=1")
                        .header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations[0].rank").value(1))
                .andReturn();
        JsonNode recs = mapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("recommendations");
        double previous = -1;
        for (JsonNode rec : recs) {
            double eta = rec.get("etaMinutes").asDouble();
            assertTrue(eta >= previous);
            previous = eta;
        }
    }

    @Test
    @Order(10)
    void operatorManagementIsAdminOnly() throws Exception {
        mvc.perform(post("/api/admin/operators")
                        .header("Authorization", "Bearer " + trafficToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"op1\",\"email\":\"op1@x.io\",\"password\":\"pass123\",\"role\":\"TRAFFIC_OPERATOR\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/operators")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"op1\",\"email\":\"op1@x.io\",\"password\":\"pass123\",\"role\":\"TRAFFIC_OPERATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TRAFFIC_OPERATOR"));
    }
}
