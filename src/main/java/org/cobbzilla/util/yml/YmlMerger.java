package org.cobbzilla.util.yml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.github.mustachejava.DefaultMustacheFactory;

public class YmlMerger {
    
    private static final String PATHS = "paths";

    private static final Logger LOG = LoggerFactory.getLogger(YmlMerger.class);
    private static final DefaultMustacheFactory DEFAULT_MUSTACHE_FACTORY = new DefaultMustacheFactory();

    private final Yaml yaml = new Yaml();
    private final Map<String, Object> scope = new HashMap<>();
    
    private final String tagFilter;
    private Map<String, Object> pathsMap = new LinkedHashMap<>();

    public YmlMerger(String tagFilter) {
        this.tagFilter = tagFilter;
        init(System.getenv());
    }

    private void init(Map<String, String> env) {
        for (String varname : env.keySet()) {
            scope.put(varname, env.get(varname));
        }
    }

    public static void main(String[] args) throws Exception {
        if(args.length < 3) {
            System.out.println("Invalid arguments.");
            System.exit(1);
        }
        
        String filter = args[0];
        String[] files = Arrays.copyOfRange(args, 1, args.length); 
        
        System.out.println(new YmlMerger(filter).mergeToString(files));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> merge(String[] files) throws IOException {
        Map<String, Object> mergedResult = new LinkedHashMap<>();
        for (String file : files) {
            InputStream in = null;
            try {
                // read the file into a String
                in = new FileInputStream(file);
                final String entireFile = IOUtils.toString(in);

                // substitute variables
                final StringWriter writer = new StringWriter(entireFile.length() + 10);
                DEFAULT_MUSTACHE_FACTORY.compile(new StringReader(entireFile), "mergeyml_" + System.currentTimeMillis()).execute(writer, scope);

                // load the YML file
                final Map<String, Object> yamlContents = (Map<String, Object>)yaml.load(writer.toString());
                
                // adding empty 'paths' key for recursion purposes
                mergedResult.putIfAbsent(PATHS, pathsMap);

                // merge into results map
                merge_internal(mergedResult, yamlContents);
                LOG.info("loaded YML from {}", file);

            } finally {
                if (in != null) in.close();
            }
        }
        return mergedResult;
    }

    @SuppressWarnings("unchecked")
    private void merge_internal(Map<String, Object> mergedResult, Map<String, Object> yamlContents) {
        
        if (yamlContents == null) return;

        for (String key : yamlContents.keySet()) {

            Object yamlValue = yamlContents.get(key);
            if (yamlValue == null) {
                addToMergedResult(mergedResult, key, yamlValue);
                continue;
            }

            Object existingValue = mergedResult.get(key);
            if (existingValue != null) {
                if (yamlValue instanceof Map) {
                    if (existingValue instanceof Map) {
                        merge_internal((Map<String, Object>)existingValue, (Map<String, Object>)yamlValue);
                    } else if (existingValue instanceof String) {
                        throw new IllegalArgumentException("Cannot merge complex element into a simple element: " + key);
                    } else {
                        throw unknownValueType(key, yamlValue);
                    }
                } else if (yamlValue instanceof List) {
                    mergeLists(mergedResult, key, yamlValue);

                } else if (yamlValue instanceof String
                        || yamlValue instanceof Boolean
                        || yamlValue instanceof Double
                        || yamlValue instanceof Integer) {
                    LOG.info("overriding value of "+key+" with value "+yamlValue);
                    addToMergedResult(mergedResult, key, yamlValue);

                } else {
                    throw unknownValueType(key, yamlValue);
                }

            } else {
                if (yamlValue instanceof Map
                        || yamlValue instanceof List
                        || yamlValue instanceof String
                        || yamlValue instanceof Boolean
                        || yamlValue instanceof Integer
                        || yamlValue instanceof Double) {
                    LOG.info("adding new key->value: "+key+"->"+yamlValue);
                    addToMergedResult(mergedResult, key, yamlValue);
                } else {
                    throw unknownValueType(key, yamlValue);
                }
            }
        }
    }

    private IllegalArgumentException unknownValueType(String key, Object yamlValue) {
        final String msg = "Cannot merge element of unknown type: " + key + ": " + yamlValue.getClass().getName();
        LOG.error(msg);
        return new IllegalArgumentException(msg);
    }

    private void addToMergedResult(Map<String, Object> mergedResult, String key, Object yamlValue) {
        if(key.startsWith("/") && yamlValue instanceof Map) {
            addByTagFilter(mergedResult, key, yamlValue);
        } else {
            mergedResult.put(key, yamlValue);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void addByTagFilter(Map<String, Object> mergedResult, String key, Object yamlValue) {
        Map<String, Object> tmpMap = new LinkedHashMap<>();
        Map<String, Object> methodMap = (Map<String, Object>) yamlValue;
        
        for(String methodKey : methodMap.keySet()) {
            Map<String, Object> tagsMap = (Map<String, Object>) methodMap.get(methodKey);
            ArrayList<String> tags = (ArrayList<String>) tagsMap.get("tags");
            
            if(tags.contains(tagFilter)) {
                LOG.info("Adding endpoint by tag: [{}]{}", methodKey, key);
                tmpMap.put(methodKey, methodMap.get(methodKey));
            }
            
            if(!tmpMap.isEmpty()) {
                pathsMap.put(key, tmpMap);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeLists(Map<String, Object> mergedResult, String key, Object yamlValue) {
        if (!(yamlValue instanceof List && mergedResult.get(key) instanceof List)) {
            throw new IllegalArgumentException("Cannot merge a list with a non-list: " + key);
        }

        List<Object> originalList = (List<Object>)mergedResult.get(key);
        originalList.addAll((List<Object>)yamlValue);
    }

    public String mergeToString(String[] files) throws IOException {
        return toString(merge(files));
    }

    public String mergeToString(List<String> files) throws IOException {
        return toString(merge(files.toArray(new String[files.size()])));
    }

    public String toString(Map<String, Object> merged) {
        return yaml.dump(merged);
    }

}
