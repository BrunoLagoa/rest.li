/*
   Copyright (c) 2012 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

/**
 * $Id: $
 */

package com.linkedin.restli.internal.server.methods.response;


import com.linkedin.data.DataMap;
import com.linkedin.data.collections.CheckedUtil;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.r2.message.rest.RestRequest;
import com.linkedin.restli.common.BatchCreateIdResponse;
import com.linkedin.restli.common.CreateIdEntityStatus;
import com.linkedin.restli.common.CreateIdStatus;
import com.linkedin.restli.common.HttpStatus;
import com.linkedin.restli.internal.common.ProtocolVersionUtil;
import com.linkedin.restli.internal.server.RestLiResponseEnvelope;
import com.linkedin.restli.internal.server.methods.AnyRecord;
import com.linkedin.restli.internal.server.response.CreateCollectionResponseEnvelope;
import com.linkedin.restli.internal.server.RoutingResult;
import com.linkedin.restli.internal.server.util.RestUtils;
import com.linkedin.restli.server.BatchCreateKVResult;
import com.linkedin.restli.server.BatchCreateResult;
import com.linkedin.restli.server.CreateResponse;
import com.linkedin.restli.server.CreateKVResponse;
import com.linkedin.restli.server.RestLiResponseData;
import com.linkedin.restli.server.RestLiServiceException;
import com.linkedin.restli.server.ResourceContext;


import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class BatchCreateResponseBuilder implements RestLiResponseBuilder
{
  private final ErrorResponseBuilder _errorResponseBuilder;

  public BatchCreateResponseBuilder(ErrorResponseBuilder errorResponseBuilder)
  {
    _errorResponseBuilder = errorResponseBuilder;
  }

  @Override
  @SuppressWarnings("unchecked")
  public PartialRestResponse buildResponse(RoutingResult routingResult, RestLiResponseData responseData)
  {
    List<CreateCollectionResponseEnvelope.CollectionCreateResponseItem> collectionCreateResponses =
                                                responseData.getCreateCollectionResponseEnvelope().getCreateResponses();
    List<CreateIdStatus<Object>> formattedResponses = new ArrayList<CreateIdStatus<Object>>(collectionCreateResponses.size());

    // Iterate through the responses and generate the ErrorResponse with the appropriate override for exceptions.
    // Otherwise, add the result as is.
    for (CreateCollectionResponseEnvelope.CollectionCreateResponseItem response : collectionCreateResponses)
    {
      if (response.isErrorResponse())
      {
        RestLiServiceException exception = response.getException();
        formattedResponses.add(new CreateIdStatus<Object>(exception.getStatus().getCode(),
                                                          response.getId(),
                                                          _errorResponseBuilder.buildErrorResponse(exception),
                                                          ProtocolVersionUtil.extractProtocolVersion(responseData.getHeaders())));
      }
      else
      {
        formattedResponses.add((CreateIdStatus<Object>) response.getRecord());
      }
    }

    PartialRestResponse.Builder builder = new PartialRestResponse.Builder();
    BatchCreateIdResponse<Object> batchCreateIdResponse = new BatchCreateIdResponse<Object>(formattedResponses);
    return builder.headers(responseData.getHeaders()).cookies(responseData.getCookies()).entity(batchCreateIdResponse).build();
  }

  @Override
  public RestLiResponseEnvelope buildRestLiResponseData(RestRequest request,
                                                        RoutingResult routingResult,
                                                        Object result,
                                                        Map<String, String> headers,
                                                        List<HttpCookie> cookies)
  {
    if (result instanceof BatchCreateKVResult)
    {
      BatchCreateKVResult<?, ?> list = (BatchCreateKVResult<?, ?>)result;
      if (list.getResults() == null)
      {
        throw new RestLiServiceException(HttpStatus.S_500_INTERNAL_SERVER_ERROR,
                                         "Unexpected null encountered. Null List inside of a BatchCreateKVResult returned by the resource method: " + routingResult
                                             .getResourceMethod());
      }
      List<CreateCollectionResponseEnvelope.CollectionCreateResponseItem> collectionCreateList = new ArrayList<CreateCollectionResponseEnvelope.CollectionCreateResponseItem>(list.getResults().size());

      for (CreateKVResponse e : list.getResults())
      {
        if (e == null)
        {
          throw new RestLiServiceException(HttpStatus.S_500_INTERNAL_SERVER_ERROR,
                                           "Unexpected null encountered. Null element inside of List inside of a BatchCreateResult returned by the resource method: "
                                               + routingResult.getResourceMethod());
        }
        else
        {
          Object id = ResponseUtils.translateCanonicalKeyToAlternativeKeyIfNeeded(e.getId(), routingResult);
          if (e.getError() == null)
          {
            final ResourceContext resourceContext = routingResult.getContext();
            DataMap entityData = e.getEntity() != null ? e.getEntity().data() : null;
            final DataMap data = RestUtils.projectFields(entityData,
                                                         resourceContext.getProjectionMode(),
                                                         resourceContext.getProjectionMask());

            CreateIdEntityStatus<Object, RecordTemplate> entry = new CreateIdEntityStatus<Object, RecordTemplate>(e.getStatus().getCode(),
                                                                                                                  id,
                                                                                                                  new AnyRecord(data),
                                                                                                                  null,
                                                                                                                  ProtocolVersionUtil.extractProtocolVersion(headers));
            collectionCreateList.add(new CreateCollectionResponseEnvelope.CollectionCreateResponseItem(entry));

          }
          else
          {
            collectionCreateList.add(new CreateCollectionResponseEnvelope.CollectionCreateResponseItem(e.getError(), id));
          }
        }
      }
      return new CreateCollectionResponseEnvelope(collectionCreateList, headers, cookies);
    }
    else
    {
      BatchCreateResult<?, ?> list = (BatchCreateResult<?, ?>) result;

      //Verify that a null list was not passed into the BatchCreateResult. If so, this is a developer error.
      if (list.getResults() == null)
      {
        throw new RestLiServiceException(HttpStatus.S_500_INTERNAL_SERVER_ERROR,
                                         "Unexpected null encountered. Null List inside of a BatchCreateResult returned by the resource method: " + routingResult
                                             .getResourceMethod());
      }

      List<CreateCollectionResponseEnvelope.CollectionCreateResponseItem> collectionCreateList = new ArrayList<CreateCollectionResponseEnvelope.CollectionCreateResponseItem>(list.getResults().size());
      for (CreateResponse e : list.getResults())
      {
        //Verify that a null element was not passed into the BatchCreateResult list. If so, this is a developer error.
        if (e == null)
        {
          throw new RestLiServiceException(HttpStatus.S_500_INTERNAL_SERVER_ERROR,
                                           "Unexpected null encountered. Null element inside of List inside of a BatchCreateResult returned by the resource method: "
                                               + routingResult.getResourceMethod());
        }
        else
        {
          Object id = ResponseUtils.translateCanonicalKeyToAlternativeKeyIfNeeded(e.getId(), routingResult);
          if (e.getError() == null)
          {
            CreateIdStatus<Object> entry = new CreateIdStatus<Object>(e.getStatus().getCode(), id, null, ProtocolVersionUtil.extractProtocolVersion(headers));
            collectionCreateList.add(new CreateCollectionResponseEnvelope.CollectionCreateResponseItem(entry));
          }
          else
          {
            collectionCreateList.add(new CreateCollectionResponseEnvelope.CollectionCreateResponseItem(e.getError(), id));
          }
        }
      }

      return new CreateCollectionResponseEnvelope(collectionCreateList, headers, cookies);
    }
  }
}
