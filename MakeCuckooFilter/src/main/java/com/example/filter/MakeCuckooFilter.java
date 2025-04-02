/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.example.filter;

import com.google.common.hash.Funnels;
import com.google.setfilters.cuckoofilter.CuckooFilter;
import com.google.setfilters.cuckoofilter.CuckooFilterConfig;
import com.google.setfilters.cuckoofilter.CuckooFilterHashFunctions;
import com.google.setfilters.cuckoofilter.CuckooFilterStrategies;
import com.google.setfilters.cuckoofilter.SerializedCuckooFilterTable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/** Replace lists of targeting criteria in a JSON ad repository with serialized cuckoo filters. */
public class MakeCuckooFilter {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println(
                    "Usage: java -jar MakeCuckooFilter.jar <src_file_name> [target_fp_rate]");
            System.out.println("");
            System.out.println("src_file_name: Input JSON ad repository file");
            System.out.println(
                    "target_fp_rate: Target false positive rate. Optional, default 0.01");
            return;
        }
        JSONObject json = readFile(args[0]);
        double targetFpRate = 0.01;
        if (args.length > 1) {
            targetFpRate = Double.parseDouble(args[1]);
        }
        JSONArray contents = json.getJSONArray("contents");
        for (int i = 0; i < contents.length(); i++) {
            JSONObject row = contents.getJSONObject(i);
            String dataStr = row.getString("data");
            if (dataStr.isEmpty() || dataStr.charAt(0) != '{') {
                continue;
            }
            JSONObject data = new JSONObject(dataStr);
            if (data.keySet().contains("excludes")) {
                JSONArray values = data.getJSONArray("excludes");
                data.put("excludeFilter", createFilter(values, targetFpRate, values.length()));
                data.remove("excludes");
            }
            if (data.keySet().contains("keywords")) {
                JSONArray values = data.getJSONArray("keywords");
                data.put("keywordFilter", createFilter(values, targetFpRate, values.length()));
                data.remove("keywords");
            }
            if (data.keySet().contains("apps")) {
                JSONArray values = data.getJSONArray("apps");
                data.put("appFilter", createFilter(values, targetFpRate, values.length()));
                data.remove("apps");
            }
            row.put("data", data.toString());
        }
        System.out.println(json.toString(2));
    }

    static JSONObject readFile(String filename) throws Exception {
        JSONObject result =
                new JSONObject(Files.readString(Path.of(filename), StandardCharsets.UTF_8));
        return result;
    }

    static String createFilter(JSONArray contents, double targetFpRate, long countUpperBound) {
        CuckooFilterConfig config =
                CuckooFilterConfig.newBuilder()
                        .setSize(
                                CuckooFilterConfig.Size.computeEfficientSize(
                                        targetFpRate, countUpperBound))
                        .setHashFunction(CuckooFilterHashFunctions.MURMUR3_128)
                        .setStrategy(CuckooFilterStrategies.SIMPLE_MOD)
                        .build();
        CuckooFilter<String> filter =
                CuckooFilter.createNew(config, Funnels.stringFunnel(StandardCharsets.UTF_8));
        for (int i = 0; i < contents.length(); i++) {
            filter.insert(contents.getString(i));
        }
        SerializedCuckooFilterTable serializedFilter = filter.serializeTable();
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(serializedFilter.asByteArray());
    }

    private MakeCuckooFilter() {}
}
