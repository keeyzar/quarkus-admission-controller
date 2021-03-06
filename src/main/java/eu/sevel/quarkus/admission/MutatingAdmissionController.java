package eu.sevel.quarkus.admission;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.admission.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.AdmissionResponseBuilder;
import io.fabric8.kubernetes.api.model.admission.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.AdmissionReviewBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonPatch;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static javax.json.bind.JsonbConfig.FORMATTING;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/mutate")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class MutatingAdmissionController {

    private static final Logger log = LoggerFactory.getLogger(MutatingAdmissionController.class);

    @POST
    public AdmissionReview validate(AdmissionReview review) {

        Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().setProperty(FORMATTING, true));
        log.info("received admission review: {}", jsonb.toJson(review));


        AdmissionRequest request = review.getRequest();

        AdmissionResponseBuilder responseBuilder = new AdmissionResponseBuilder()
                .withAllowed(true)
                .withUid(request.getUid());

        HasMetadata object = request.getObject();

        if (request.getOperation().equals("CREATE") && object instanceof Deployment) {

            if (needsMutating(object)) {

                JsonObject original = toJsonObject(object);

                mutate(object);

                JsonObject mutated = toJsonObject(object);

                String patch = Json.createDiff(original, mutated).toString();
                String encoded = Base64.getEncoder().encodeToString(patch.getBytes());
                log.info("patching with {}", patch);

                responseBuilder
                        .withPatchType("JSONPatch")
                        .withPatch(encoded);
            }

        }

        return new AdmissionReviewBuilder().withResponse(responseBuilder.build()).build();
    }

    JsonObject toJsonObject(HasMetadata object) {

        return Json.createReader(new StringReader(JsonbBuilder.create().toJson(object))).readObject();
    }

    boolean needsMutating(HasMetadata object) {
        ObjectMeta metadata = object.getMetadata();
        Map<String, String> labels = metadata.getLabels();
        return labels == null || !labels.containsKey("foo");
    }

    void mutate(HasMetadata object) {
        ObjectMeta metadata = object.getMetadata();
        Map<String, String> labels = metadata.getLabels();
        if (labels == null) {
            labels = new HashMap<>();
            metadata.setLabels(labels);
        }
        labels.put("foo", "bar");
    }
}