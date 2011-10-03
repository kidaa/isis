package org.apache.isis.viewer.json.viewer.resources.home;

import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.viewer.json.applib.JsonRepresentation;
import org.apache.isis.viewer.json.applib.RepresentationType;
import org.apache.isis.viewer.json.applib.RestfulRequest.QueryParameter;
import org.apache.isis.viewer.json.viewer.ResourceContext;
import org.apache.isis.viewer.json.viewer.representations.LinkReprBuilder;
import org.apache.isis.viewer.json.viewer.representations.RendererFactory;
import org.apache.isis.viewer.json.viewer.representations.RendererFactoryRegistry;
import org.apache.isis.viewer.json.viewer.representations.ReprRenderer;
import org.apache.isis.viewer.json.viewer.representations.ReprRendererAbstract;
import org.apache.isis.viewer.json.viewer.representations.ReprRendererFactoryAbstract;
import org.apache.isis.viewer.json.viewer.resources.domainobjects.DomainServiceLinkToBuilder;
import org.apache.isis.viewer.json.viewer.resources.domainobjects.ListReprRenderer;
import org.apache.isis.viewer.json.viewer.resources.user.UserReprRenderer;

public class HomePageReprRenderer extends ReprRendererAbstract<HomePageReprRenderer, Void> {

    public static class Factory extends ReprRendererFactoryAbstract {
        public Factory() {
            super(RepresentationType.HOME_PAGE);
        }

        @Override
        public ReprRenderer<?, ?> newRenderer(ResourceContext resourceContext, JsonRepresentation representation) {
            return new HomePageReprRenderer(resourceContext, getRepresentationType(), representation);
        }
    }

    private HomePageReprRenderer(ResourceContext resourceContext, RepresentationType representationType, JsonRepresentation representation) {
        super(resourceContext, representationType, representation);
    }

    @Override
    public HomePageReprRenderer with(Void t) {
        return this;
    }

    @Override
    public JsonRepresentation render() {
        
        // self
        if(includesSelf) {
            withSelf("");
        }

        // user
        putLinkToUser(representation);

        // services
        putLinkToServices(representation);
        
        // capabilities
        putLinkToCapabilities(representation);

        // links and extensions
        representation.mapPut("links", JsonRepresentation.newArray());
        representation.mapPut("extensions", JsonRepresentation.newMap());
        
        return representation;
    }

    private void putLinkToCapabilities(final JsonRepresentation representation) {
        representation.mapPut("capabilities", 
                LinkReprBuilder.newBuilder(getResourceContext(), "capabilities", RepresentationType.CAPABILITIES, "capabilities").render());
    }

    private void putLinkToServices(JsonRepresentation representation) {

        final LinkReprBuilder servicesLinkBuilder = 
                LinkReprBuilder.newBuilder(getResourceContext(), "services", RepresentationType.LIST, "services");
        
        final List<String> followLinks = getResourceContext().getArg(QueryParameter.FOLLOW_LINKS);
        if(followLinks.contains("services")) {
            
            final List<ObjectAdapter> serviceAdapters = getResourceContext().getPersistenceSession().getServices();

            final RendererFactory factory = RendererFactoryRegistry.instance.find(RepresentationType.LIST);
            
            final ListReprRenderer renderer = (ListReprRenderer) factory.newRenderer(getResourceContext(), JsonRepresentation.newMap());
            renderer.usingLinkToBuilder(new DomainServiceLinkToBuilder())
                    .withSelf("services")
                    .with(serviceAdapters);
            
            servicesLinkBuilder.withValue(renderer.render());
        }
        
        representation.mapPut("services", servicesLinkBuilder.render());
    }

    private void putLinkToUser(JsonRepresentation representation) {
        final LinkReprBuilder userLinkBuilder = 
                LinkReprBuilder.newBuilder(getResourceContext(), "user", RepresentationType.USER, "user");
        
        final List<String> followLinks = getResourceContext().getArg(QueryParameter.FOLLOW_LINKS);
        if(followLinks.contains("user")) {
            
            final RendererFactory factory = RendererFactoryRegistry.instance.find(RepresentationType.USER);
            final UserReprRenderer renderer = 
                    (UserReprRenderer) factory.newRenderer(getResourceContext(), JsonRepresentation.newMap());
            renderer.with(getResourceContext().getAuthenticationSession());
            
            userLinkBuilder.withValue(renderer.render());
        }
        
        representation.mapPut("user", userLinkBuilder.render());
    }

}