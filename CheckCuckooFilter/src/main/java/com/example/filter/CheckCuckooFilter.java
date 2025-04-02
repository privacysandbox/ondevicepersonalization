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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.hash.Funnels;
import com.google.setfilters.cuckoofilter.CuckooFilter;
import com.google.setfilters.cuckoofilter.CuckooFilterHashFunctions;
import com.google.setfilters.cuckoofilter.CuckooFilterStrategies;
import com.google.setfilters.cuckoofilter.SerializedCuckooFilterTable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Random;

/** Checks the correctness of the cuckoo filter. */
public class CheckCuckooFilter {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println(
                    "Usage: java -jar CheckCuckooFilter.jar <src_file_name>"
                            + " <cuckoo_filter_file_name> [target_fp_rate]");
            System.out.println();
            System.out.println("src_file_name: Input JSON ad repository file");
            System.out.println(
                    "cuckoo_filter_file_name: Input JSON cuckoo filter output by"
                            + " MakeCuckooFilter.jar");
            System.out.println(
                    "target_fp_rate: Target false positive rate used in MakeCuckooFilter.java."
                            + " Default to 0.01");
            return;
        }

        double targetFpRate = 0.01;
        if (args.length > 2) {
            targetFpRate = Double.parseDouble(args[2]);
            if (targetFpRate > 0.5) {
                throw new IllegalArgumentException(
                        "Target false positive rate is too large, must be at " + " most 0.5.");
            }
            if (targetFpRate < Math.pow(0.5, 16)) {
                throw new IllegalArgumentException(
                        "Target false positive rate is too small, must be at " + " least 2^(-16).");
            }
        }

        JSONObject json = readFile(args[0]);
        JSONArray contents = json.getJSONArray("contents");

        JSONObject cuckooFilterJson = readFile(args[1]);
        JSONArray cuckooFilterContents = cuckooFilterJson.getJSONArray("contents");
        for (int i = 0; i < contents.length(); i++) {
            JSONObject row = contents.getJSONObject(i);
            String dataStr = row.getString("data");
            if (dataStr.isEmpty() || dataStr.charAt(0) != '{') {
                continue;
            }
            JSONObject data = new JSONObject(dataStr);

            JSONObject cuckooFilterRow = cuckooFilterContents.getJSONObject(i);
            String cuckooFilterDataStr = cuckooFilterRow.getString("data");
            if (cuckooFilterDataStr.isEmpty() || cuckooFilterDataStr.charAt(0) != '{') {
                continue;
            }
            JSONObject cuckooFilterData = new JSONObject(cuckooFilterDataStr);
            if (data.keySet().contains("excludes")) {
                JSONArray values = data.getJSONArray("excludes");
                String serializedFilter = cuckooFilterData.getString("excludeFilter");
                checkFilterCorrectness(values, serializedFilter, targetFpRate);
            }
            if (data.keySet().contains("keywords")) {
                JSONArray values = data.getJSONArray("keywords");
                String serializedFilter = cuckooFilterData.getString("keywordFilter");
                checkFilterCorrectness(values, serializedFilter, targetFpRate);
            }
            if (data.keySet().contains("apps")) {
                JSONArray values = data.getJSONArray("apps");
                String serializedFilter = cuckooFilterData.getString("appFilter");
                checkFilterCorrectness(values, serializedFilter, targetFpRate);
            }
        }
        System.out.println("Correctness check done! Constructed cuckoo filters are correct.");
    }

    static JSONObject readFile(String filename) throws Exception {
        JSONObject result = new JSONObject(Files.readString(Path.of(filename), UTF_8));
        return result;
    }

    static CuckooFilter<String> deserializeFilter(String serializedFilter) {
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] filterBytes = decoder.decode(serializedFilter);
        SerializedCuckooFilterTable serializedTable =
                SerializedCuckooFilterTable.createFromByteArray(filterBytes);
        return CuckooFilter.createFromSerializedTable(
                serializedTable,
                CuckooFilterHashFunctions.MURMUR3_128,
                CuckooFilterStrategies.SIMPLE_MOD,
                Funnels.stringFunnel(UTF_8));
    }

    static void checkFilterCorrectness(
            JSONArray values, String serializedFilter, double targetFpRate) {
        CuckooFilter<String> filter = deserializeFilter(serializedFilter);
        for (int i = 0; i < values.length(); i++) {
            if (!filter.contains(values.getString(i))) {
                throw new IllegalStateException("Cuckoo filter returned false negative!");
            }
        }
        System.out.println("False negative check OK. No false negatives.");

        int numSamples = (int) (1000 / targetFpRate);
        Random random = new Random();
        int numFalsePositives = 0;
        for (int i = 0; i < numSamples; i++) {
            if (filter.contains(random.nextLong() + "")) {
                numFalsePositives++;
            }
        }

        double measuredFpRate = numFalsePositives / (double) numSamples;
        // Negative implies measured FP rate was smaller than target FP rate, which we are okay
        // with.
        // Target FP rate should be an upper bound of the measured FP rate.
        double relativeDifference = (measuredFpRate - targetFpRate) / targetFpRate;
        if (relativeDifference > 0.05) {
            throw new IllegalStateException(
                    "Measured false positives too large, relative difference of "
                            + relativeDifference);
        } else {
            System.out.println(
                    "False positive check OK. measured FP rate: "
                            + measuredFpRate
                            + ", target FP rate: "
                            + targetFpRate);
        }
    }

    private CheckCuckooFilter() {}
}
