/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.ranger.authorization.utils.JsonUtils;
import org.apache.ranger.common.SearchField;
import org.apache.ranger.common.SearchField.DATA_TYPE;
import org.apache.ranger.common.SearchField.SEARCH_TYPE;
import org.apache.ranger.common.SortField;
import org.apache.ranger.entity.XXServiceResource;
import org.apache.ranger.entity.XXTag;
import org.apache.ranger.plugin.model.RangerTag;
import org.apache.ranger.plugin.util.SearchFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RangerTagService extends RangerTagServiceBase<XXTag, RangerTag> {
    private static final Logger logger = LoggerFactory.getLogger(RangerTagService.class);

    private static final TypeReference<Map<String, String>> subsumedDataType = new TypeReference<Map<String, String>>() {};

    public RangerTagService() {
        searchFields.add(new SearchField(SearchFilter.TAG_ID, "obj.id", SearchField.DATA_TYPE.INTEGER, SearchField.SEARCH_TYPE.FULL));
        searchFields.add(new SearchField(SearchFilter.TAG_DEF_ID, "obj.type", SearchField.DATA_TYPE.INTEGER, SearchField.SEARCH_TYPE.FULL));
        searchFields.add(new SearchField(SearchFilter.TAG_TYPE, "tagDef.name", DATA_TYPE.STRING, SEARCH_TYPE.FULL, "XXTagDef tagDef", "obj.type = tagDef.id"));
        searchFields.add(new SearchField(SearchFilter.TAG_TYPE_PARTIAL, "tagDef.name", DATA_TYPE.STRING, SEARCH_TYPE.PARTIAL, "XXTagDef tagDef", "obj.type = tagDef.id"));
        searchFields.add(new SearchField(SearchFilter.TAG_IDS, "obj.id", SearchField.DATA_TYPE.INT_LIST, SearchField.SEARCH_TYPE.FULL));

        sortFields.add(new SortField(SearchFilter.TAG_ID, "obj.id", true, SortField.SORT_ORDER.ASC));
        sortFields.add(new SortField(SearchFilter.TAG_DEF_ID, "obj.type"));
        sortFields.add(new SortField(SearchFilter.CREATE_TIME, "obj.createTime"));
        sortFields.add(new SortField(SearchFilter.UPDATE_TIME, "obj.updateTime"));
    }

    public RangerTag getPopulatedViewObject(XXTag xObj) {
        return populateViewBean(xObj);
    }

    public RangerTag getTagByGuid(String guid) {
        RangerTag ret   = null;
        XXTag     xxTag = daoMgr.getXXTag().findByGuid(guid);

        if (xxTag != null) {
            ret = populateViewBean(xxTag);
        }

        return ret;
    }

    public List<RangerTag> getTagsByType(String name) {
        List<RangerTag> ret    = new ArrayList<>();
        List<XXTag>     xxTags = daoMgr.getXXTag().findByName(name);

        if (CollectionUtils.isNotEmpty(xxTags)) {
            for (XXTag xxTag : xxTags) {
                RangerTag tag = populateViewBean(xxTag);

                ret.add(tag);
            }
        }

        return ret;
    }

    public List<RangerTag> getTagsForResourceId(Long resourceId) {
        List<RangerTag>   ret                   = new ArrayList<>();
        XXServiceResource serviceResourceEntity = daoMgr.getXXServiceResource().getById(resourceId);

        if (serviceResourceEntity != null) {
            String tagsText = serviceResourceEntity.getTags();

            if (StringUtils.isNotEmpty(tagsText)) {
                try {
                    ret = JsonUtils.jsonToObject(tagsText, RangerServiceResourceService.duplicatedDataType);
                } catch (JsonProcessingException e) {
                    logger.error("Error occurred while processing json", e);
                }
            }
        }

        return ret;
    }

    public List<RangerTag> getTagsForResourceGuid(String resourceGuid) {
        List<RangerTag>   ret                   = new ArrayList<>();
        XXServiceResource serviceResourceEntity = daoMgr.getXXServiceResource().findByGuid(resourceGuid);

        if (serviceResourceEntity != null) {
            String tagsText = serviceResourceEntity.getTags();

            if (StringUtils.isNotEmpty(tagsText)) {
                try {
                    ret = JsonUtils.jsonToObject(tagsText, RangerServiceResourceService.duplicatedDataType);
                } catch (JsonProcessingException e) {
                    logger.error("Error occurred while processing json", e);
                }
            }
        }

        return ret;
    }

    public List<RangerTag> getTagsByServiceId(Long serviceId) {
        List<RangerTag> ret    = new ArrayList<>();
        List<XXTag>     xxTags = daoMgr.getXXTag().findByServiceId(serviceId);

        if (CollectionUtils.isNotEmpty(xxTags)) {
            for (XXTag xxTag : xxTags) {
                RangerTag tag = populateViewBean(xxTag);

                ret.add(tag);
            }
        }

        return ret;
    }

    @Override
    public RangerTag postCreate(XXTag tag) {
        RangerTag ret = super.postCreate(tag);

        // This is not needed - on tag creation, service-version-info need not be updated.
        //daoMgr.getXXServiceVersionInfo().updateServiceVersionInfoForTagUpdate(tag.getId());

        return ret;
    }

    @Override
    public RangerTag postUpdate(XXTag tag) {
        RangerTag ret = super.postUpdate(tag);

        daoMgr.getXXServiceVersionInfo().updateServiceVersionInfoForTagUpdate(tag.getId());

        return ret;
    }

    @Override
    protected void validateForCreate(RangerTag vObj) {
    }

    @Override
    protected void validateForUpdate(RangerTag vObj, XXTag entityObj) {
    }

    @Override
    protected XXTag preDelete(Long id) {
        XXTag ret = super.preDelete(id);

        daoMgr.getXXServiceVersionInfo().updateServiceVersionInfoForTagUpdate(id);

        return ret;
    }

    @Override
    public Map<String, String> getAttributesForTag(XXTag xTag) {
        return new HashMap<>();
    }

    @Override
    protected XXTag mapViewToEntityBean(RangerTag vObj, XXTag xObj, int operationContext) {
        XXTag ret = super.mapViewToEntityBean(vObj, xObj, operationContext);

        ret.setTagAttrs(JsonUtils.mapToJson(vObj.getAttributes()));

        return ret;
    }

    @Override
    protected RangerTag mapEntityToViewBean(RangerTag vObj, XXTag xObj) {
        RangerTag ret = super.mapEntityToViewBean(vObj, xObj);

        if (StringUtils.isNotEmpty(xObj.getTagAttrs())) {
            try {
                Map<String, String> attributes = JsonUtils.jsonToObject(xObj.getTagAttrs(), RangerTagService.subsumedDataType);

                ret.setAttributes(attributes);
            } catch (JsonProcessingException e) {
                logger.error("Error occurred while processing json", e);
            }
        }

        return ret;
    }
}
