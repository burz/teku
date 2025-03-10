/*
 * Copyright ConsenSys Software Inc., 2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.COMMITTEE_INDEX_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.GetAttestationDataResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;

public class GetAttestationData extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/validator/attestation_data";
  private final ValidatorDataProvider provider;

  private static final ParameterMetadata<UInt64> SLOT_PARAM =
      SLOT_PARAMETER.withDescription(
          "`uint64` The slot for which an attestation data should be created.");

  private static final SerializableTypeDefinition<AttestationData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(AttestationData.class)
          .name("ProduceAttestationDataResponse")
          .withField(
              "data", AttestationData.SSZ_SCHEMA.getJsonTypeDefinition(), Function.identity())
          .build();

  public GetAttestationData(final DataProvider provider) {
    this(provider.getValidatorDataProvider());
  }

  public GetAttestationData(final ValidatorDataProvider provider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getAttestationData")
            .summary("Produce an AttestationData")
            .description("Requests that the beacon node produce an AttestationData.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .queryParam(SLOT_PARAM)
            .queryParam(
                COMMITTEE_INDEX_PARAMETER.withDescription(
                    "`UInt64` The committee index for which an attestation data should be created."))
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Produce an AttestationData",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      queryParams = {
        @OpenApiParam(
            name = SLOT,
            description = "`uint64` The slot for which an attestation data should be created.",
            required = true),
        @OpenApiParam(
            name = COMMITTEE_INDEX,
            type = Integer.class,
            description =
                "`Integer` The committee index for which an attestation data should be created.",
            required = true)
      },
      description =
          "Returns attestation data for the block at the specified non-finalized slot.\n\n"
              + "This endpoint is not protected against slashing. Signing the returned attestation data can result in a slashable offence.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetAttestationDataResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot = request.getQueryParameter(SLOT_PARAM);
    final UInt64 committeeIndex = request.getQueryParameter(COMMITTEE_INDEX_PARAMETER);
    if (committeeIndex.isLessThan(0)) {
      request.respondError(
          SC_BAD_REQUEST,
          String.format("'%s' needs to be greater than or equal to 0.", COMMITTEE_INDEX));
      return;
    }

    final SafeFuture<Optional<AttestationData>> future =
        provider.createAttestationDataAtSlot(slot, committeeIndex.intValue());

    request.respondAsync(
        future.thenApply(
            maybeAttestationData ->
                maybeAttestationData
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }
}
