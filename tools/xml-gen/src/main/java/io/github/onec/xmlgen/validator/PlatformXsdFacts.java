package io.github.onec.xmlgen.validator;

import java.util.Map;
import java.util.Set;

/**
 * Platform XSD facts imported from namespace-forest 2.21 coverage analysis.
 *
 * <p>These constants are intentionally narrow facts, not a replacement for full
 * XSD validation. They make previously external schema gaps visible to xml-gen
 * validators and tests.</p>
 */
public final class PlatformXsdFacts {

    public static final String NS_BSL = "http://v8.1c.ru/8.2/data/bsl";
    public static final String NS_MANAGED_APPLICATION_MODULES =
            "http://v8.1c.ru/8.2/managed-application/modules";
    public static final String NS_UOBJECTS = "http://v8.1c.ru/8.2/uobjects";
    public static final String NS_MANAGED_APPLICATION_CMI =
            "http://v8.1c.ru/8.2/managed-application/cmi";
    public static final String NS_MANAGED_APPLICATION_CORE =
            "http://v8.1c.ru/8.2/managed-application/core";

    public static final Set<String> XSD_ONLY_NAMESPACES = Set.of(
            NS_BSL,
            NS_MANAGED_APPLICATION_MODULES,
            NS_UOBJECTS
    );

    public static final Set<String> XSD_GLOBAL_ELEMENTS_IMPORTED_FROM_DELTA = Set.of(
            "ClientApplicationInterface",
            "section"
    );

    public static final Set<String> REQUIRED_ATTRIBUTES_IMPORTED_FROM_DELTA = Set.of(
            "clsid",
            "count",
            "delta",
            "formatVersion",
            "helpTopic",
            "index1",
            "index2",
            "itemType",
            "lastId",
            "md",
            "nameInt",
            "nameRus",
            "pattern",
            "remoteKey",
            "seq",
            "seqDe",
            "seqUo",
            "sin",
            "sinDe",
            "sinUo",
            "startId",
            "total",
            "trackChanges",
            "url"
    );

    public static final Set<String> REQUIRED_ELEMENTS_IMPORTED_FROM_DELTA = Set.of(
            "longAloneMonthNames",
            "longDayNames",
            "longMonthNames",
            "shortAloneMonthNames",
            "shortDayNames",
            "shortMonthNames"
    );

    public static final Map<String, Set<String>> REQUIRED_ATTRIBUTES_BY_CMI_ROOT = Map.of(
            "section", Set.of("id", "name", "helpTopic"),
            "group", Set.of("id"),
            "command", Set.of("id", "nameInt", "nameRus", "md", "helpTopic", "url")
    );

    public static final Set<String> UOBJECTS_REQUIRED_ATTRIBUTES = Set.of(
            "count",
            "delta",
            "index1",
            "index2",
            "itemType",
            "lastId",
            "remoteKey",
            "seq",
            "seqDe",
            "seqUo",
            "sin",
            "sinDe",
            "sinUo",
            "startId",
            "total",
            "trackChanges"
    );

    private PlatformXsdFacts() {
    }
}
