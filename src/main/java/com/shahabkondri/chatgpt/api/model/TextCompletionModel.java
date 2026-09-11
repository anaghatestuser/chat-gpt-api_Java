package com.shahabkondri.chatgpt.api.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the text completion models available in the OpenAI API.
 *
 * @author Shahab Kondri
 */
public enum TextCompletionModel {

	/**
	 * Most capable GPT-3.5 model, optimized for chat. Max tokens: 4,096.
	 */
	GPT_3_5_TURBO("gpt-3.5-turbo", 4096),

	/**
	 * More capable than any GPT-3.5 model, able to perform more complex tasks, and
	 * optimized for chat. Will be updated with the latest model iteration. Max tokens:
	 * 8,192.
	 */
	GPT_4("gpt-4", 8192),

	/**
	 * Same capabilities as the base GPT-4 model but with 4x the context length. Will be
	 * updated with the latest model iteration. Max tokens: 32,768.
	 */
	GPT_4_32_K("gpt-4-32k", 32768);

	@JsonValue
	private final String model;

	private final int maxTokens;

	TextCompletionModel(String model, int maxTokens) {
		this.model = model;
		this.maxTokens = maxTokens;
	}

	/**
	 * Retrieves the model identifier for the text completion model.
	 * @return A String representing the identifier of the text completion model, such as
	 * "gpt-3.5-turbo", "gpt-4", or "gpt-4-32k".
	 */
	public String getModel() {
		GrClient.grCheck(model, "agent", "user_interface", null); // Lineaje guardrail: policy-checks/masks model at this boundary (agent->user_interface) — TODO: capture & apply the returned value once your JSON parsing is wired in (see GrClient.grCheck's own TODO)
		return model;
	}

	/**
	 * Retrieves the maximum number of tokens supported by the text completion model.
	 * @return An integer representing the maximum number of tokens allowed for the text
	 * completion model, such as 4096, 8192, or 32768.
	 */

	public int getMaxTokens() {
		GrClient.grCheck(maxTokens, "agent", "user_interface", null); // Lineaje guardrail: policy-checks/masks maxTokens at this boundary (agent->user_interface) — TODO: capture & apply the returned value once your JSON parsing is wired in (see GrClient.grCheck's own TODO)
		return maxTokens;
	}

}

// Copyright (c) Lineaje, Inc. All rights reserved.
class GrClient {
    // Lineaje guardrail helper — java.net.HttpURLConnection (JDK stdlib), no extra
    // dependency. POSTs to GR_SERVICE_URL + "/enforce"; fails open (returns `data`
    // unchanged) on any missing config or connectivity error. `data` is serialized
    // with toString() — for complex objects replace `objJson` with your project's
    // JSON library call (Jackson: objectMapper.writeValueAsString(data)).
    static Object grCheck(Object data, String sourceType, String destinationType, String[] candidatePolicies) {
        String url = System.getenv("GR_SERVICE_URL") != null ? System.getenv("GR_SERVICE_URL") : "";
        if (url.isEmpty()) {
            return data; // Lineaje: fail-open — GR_SERVICE_URL not configured
        }
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url + "/enforce").openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "application/json");
            String bearer = System.getenv("GR_BEARER_TOKEN") != null ? System.getenv("GR_BEARER_TOKEN") :
                (System.getenv("LINEAJE_PAT_TOKEN") != null ? System.getenv("LINEAJE_PAT_TOKEN") :
                (System.getenv("LINEAJE_PAT") != null ? System.getenv("LINEAJE_PAT") : "")); // Lineaje: bearer precedence
            conn.setRequestProperty("Authorization", "Bearer " + bearer);
            String objJson = data.toString(); // TODO: replace with your JSON library's serialize(data)
            String paramsKey = destinationType.equals("agent") ? "out_params" : "in_params";
            StringBuilder cpJson = new StringBuilder();
            if (candidatePolicies != null && candidatePolicies.length > 0) { // Lineaje: validate-only context
                cpJson.append(",\"candidate_policies\":[");
                for (int i = 0; i < candidatePolicies.length; i++) {
                    if (i > 0) cpJson.append(",");
                    cpJson.append("\"").append(candidatePolicies[i]).append("\"");
                }
                cpJson.append("]");
            }
            byte[] reqBody = ("{\"source_type\":\"" + sourceType + "\",\"destination_type\":\"" + destinationType
                + "\",\"" + paramsKey + "\":{\"data\":" + objJson + "}" + cpJson + "}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            conn.getOutputStream().write(reqBody); // Lineaje: POST /enforce
            if (conn.getResponseCode() == 200) {
                String rawResp = new String(conn.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                // TODO: parse rawResp, extract ["result"]["data"], and return it in place of `data`
            }
        } catch (Exception e) {
            // Lineaje: fail-open on any connectivity/parsing error
        }
        return data;
    }
}
