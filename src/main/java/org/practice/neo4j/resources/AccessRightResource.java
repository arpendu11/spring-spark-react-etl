package org.practice.neo4j.resources;

import java.net.URI;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.exceptions.NoSuchRecordException;
import org.practice.model.AccessRight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/neo/accessrights")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccessRightResource {
	
	Logger logger = LoggerFactory.getLogger(AccessRightResource.class);

	@Inject
	Driver driver;
	
	@Path("/all")
	@GET
	public CompletionStage<Response> get() {
	    AsyncSession session = driver.asyncSession(); 
	    return session
	        .runAsync("MATCH (ar:AccessRight) RETURN ar")  
	        .thenCompose(cursor ->  
	            cursor.listAsync(record -> AccessRight.from(record.get("ar").asNode()))
	        )
	        .thenCompose(accessRight ->  
	            session.closeAsync().thenApply(signal -> accessRight)
	        )
	        .thenApply(Response::ok) 
	        .thenApply(ResponseBuilder::build);
	}
	
	@POST
	public CompletionStage<Response> create(AccessRight accessRight) {
		logger.info(accessRight.toString());
	    AsyncSession session = driver.asyncSession();
	    return session
	        .writeTransactionAsync(tx -> tx
	            .runAsync("MERGE (ar:AccessRight {id: $id,"
	            		+ " type: $type,"
	            		+ " startTime: $startTime,"
	            		+ " endTime: $endTime,"
	            		+ " entitlementId: $entitlementId,"
	            		+ " entitlementName: $entitlementName,"
	            		+ " entitlementDescription: $entitlementDescription}) RETURN ar",
	            		Values.parameters("id", accessRight.getId(),
	            				"type", accessRight.getType(),
	            				"startTime", accessRight.getStartTime(),
	            				"endTime", accessRight.getEndTime(),
	            				"entitlementId", accessRight.getEntitlementId(),
	            				"entitlementName", accessRight.getEntitlementName(),
	            				"entitlementDescription", accessRight.getEntitlementDescription()))
	            .thenCompose(fn -> fn.singleAsync())
	        )
	        .thenApply(record -> AccessRight.from(record.get("ar").asNode()))
	        .thenCompose(persistedAccessRight -> session.closeAsync().thenApply(signal -> persistedAccessRight))
	        .thenApply(persistedAccessRight -> Response
	            .created(URI.create("/neo/accessrights/" + persistedAccessRight.getId()))
	            .build()
	        );
	}
	
	@GET
	@Path("/{id}")
	public CompletionStage<Response> getSingle(@PathParam("id") String id) {
	    AsyncSession session = driver.asyncSession();
	    return session
	        .readTransactionAsync(tx -> tx
	            .runAsync("MATCH (ar:AccessRight) WHERE ar.id = $id RETURN ar", Values.parameters("id", id))
	            .thenCompose(fn -> fn.singleAsync())
	    )
	    .handle((record, exception) -> {
	        if(exception != null) {
	            Throwable source = exception;
	            if(exception instanceof CompletionException) {
	                source = ((CompletionException)exception).getCause();
	            }
	            Status status = Status.INTERNAL_SERVER_ERROR;
	            if(source instanceof NoSuchRecordException) {
	                status = Status.NOT_FOUND;
	            }
	            return Response.status(status).build();
	        } else  {
	            return Response.ok(AccessRight.from(record.get("ar").asNode())).build();
	        }
	    })
	    .thenCompose(response -> session.closeAsync().thenApply(signal -> response));
	}
}
