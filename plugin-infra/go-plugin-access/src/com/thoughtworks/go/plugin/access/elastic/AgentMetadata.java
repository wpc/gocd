/*
 * Copyright 2016 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.plugin.access.elastic;

import com.google.gson.*;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class AgentMetadata {

    private static final Gson GSON = new GsonBuilder().
            excludeFieldsWithoutExposeAnnotation().
            serializeNulls().
            setFieldNamingStrategy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).
            create();

    @Expose
    @SerializedName("agent_id")
    private final String elasticAgentId;
    @Expose
    @SerializedName("agent_state")
    private final String agentState;
    @Expose
    @SerializedName("build_state")
    private final String buildState;
    @Expose
    @SerializedName("config_state")
    private final String configState;

    public AgentMetadata(String elasticAgentId, String agentState, String buildState, String configState) {
        this.elasticAgentId = elasticAgentId;
        this.agentState = agentState;
        this.buildState = buildState;
        this.configState = configState;
    }

    public String elasticAgentId() {
        return elasticAgentId;
    }

    public String agentState() {
        return agentState;
    }

    public String buildState() {
        return buildState;
    }

    public String configState() {
        return configState;
    }

    public JsonElement toJSON() {
        return GSON.toJsonTree(this);
    }
}
