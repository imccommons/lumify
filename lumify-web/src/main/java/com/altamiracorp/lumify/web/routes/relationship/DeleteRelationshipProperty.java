package com.altamiracorp.lumify.web.routes.relationship;

import com.altamiracorp.lumify.core.model.audit.AuditAction;
import com.altamiracorp.lumify.core.model.audit.AuditRepository;
import com.altamiracorp.lumify.core.model.ontology.OntologyRepository;
import com.altamiracorp.lumify.core.user.User;
import com.altamiracorp.lumify.web.BaseRequestHandler;
import com.altamiracorp.lumify.web.routes.vertex.VertexProperties;
import com.altamiracorp.miniweb.HandlerChain;
import com.altamiracorp.securegraph.*;
import com.google.inject.Inject;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

public class DeleteRelationshipProperty extends BaseRequestHandler {
    private final Graph graph;
    private final OntologyRepository ontologyRepository;
    private final AuditRepository auditRepository;

    @Inject
    public DeleteRelationshipProperty(final OntologyRepository ontologyRepository, final Graph graph, final AuditRepository auditRepository) {
        this.ontologyRepository = ontologyRepository;
        this.graph = graph;
        this.auditRepository = auditRepository;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        final String relationshipLabel = getRequiredParameter(request, "relationshipLabel");
        final String propertyName = getRequiredParameter(request, "propertyName");
        final String sourceId = getRequiredParameter(request, "source");
        final String destId = getRequiredParameter(request, "dest");

        User user = getUser(request);

        com.altamiracorp.lumify.core.model.ontology.Property property = ontologyRepository.getProperty(propertyName, user);
        if (property == null) {
            throw new RuntimeException("Could not find property: " + propertyName);
        }

        Vertex sourceVertex = graph.getVertex(sourceId, user.getAuthorizations());
        Vertex destVertex = graph.getVertex(destId, user.getAuthorizations());

        // TODO remove all properties from all edges? I don't think so
        Iterable<Edge> possibleEdges = sourceVertex.getEdges(destVertex, Direction.BOTH, relationshipLabel, user.getAuthorizations());
        for (Edge edge : possibleEdges) {
            Object oldValue = edge.getPropertyValue(propertyName, 0);
            // TODO: replace "" when we implement commenting on ui
            auditRepository.auditRelationshipProperties(AuditAction.DELETE.toString(), sourceId, destId, property.getDisplayName(), oldValue, edge, "", "", user);

            edge.removeProperty(propertyName);
        }
        graph.flush();

        // TODO get all properties from all edges?
        List<Property> properties = new ArrayList<Property>();
        possibleEdges = sourceVertex.getEdges(destVertex, Direction.BOTH, relationshipLabel, user.getAuthorizations());
        for (Edge edge : possibleEdges) {
            for (Property p : edge.getProperties()) {
                properties.add(p);
            }
        }
        JSONObject resultsJson = VertexProperties.propertiesToJson(properties);

        respondWithJson(response, resultsJson);
    }
}
