package com.ojitos369.lumaloop.preferences;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class TagExportData {
    public Set<String> catalog;
    public Map<String, List<String>> mappings; // Uri.toString() -> List<String>
    public Set<String> activeTags;
    public Set<String> hiddenTags;
    public String tagFilterMode;
    public boolean autoTagEnabled;
}
