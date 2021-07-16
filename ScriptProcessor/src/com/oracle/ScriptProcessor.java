/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScriptProcessor {
    private static final HexFormat HF = HexFormat.of().withUpperCase();

    // arg[0] is the unicodedata directory
    public static void main(String[] args) {
        try {
            final var last = new Range[1]; // last script range
            last[0] = new Range(0, 0, "", "");

            var scripts = Files.lines(Paths.get(args[0], "Scripts.txt"))
                    .filter(Predicate.not(l -> l.startsWith("#") || l.isBlank()))
                    .map(Range::new)
                    .sorted()
                    .flatMap(r -> {
                        Range unkRange = null;
                        if (last[0].last < r.start - 1) {
                            // need to insert UNKNOWN
                            unkRange = new Range(last[0].last + 1, r.start - 1, "UNKNOWN", "Unknown");
                        }
                        last[0] = r;
                        return unkRange != null ? Stream.of(unkRange, r) : Stream.of(r);
                    })
                    .collect(ArrayList<Range>::new,
                            (list, r) -> {
                                // collapsing consecutive same script ranges
                                int lastIndex = list.size() - 1;
                                if (lastIndex >= 0) {
                                    Range lastRange = list.get(lastIndex);
                                    if (lastRange.script.equals(r.script)) {
                                        list.set(lastIndex, new Range(lastRange.start, r.last, r.script, r.name));
                                        return;
                                    }
                                }
                                list.add(r);
                            },
                            ArrayList::addAll);
                    // Add the last UNKNOWN
                    scripts.add(scripts.size(),
                            new Range(scripts.get(scripts.size() - 1).last + 1, 0x10FFFF, "UNKNOWN", "Unknown"));

            // scriptStarts
            scripts.stream()
                    .map(Range::printScriptStarts)
                    .forEach(System.out::println);

            // scripts
            scripts.stream()
                    .map(Range::printScripts)
                    .forEach(System.out::println);

            // constants
            var newScripts = scripts.stream()
                    .filter(r -> {
                        try {
                            Character.UnicodeScript.forName(r.script);
                        } catch (IllegalArgumentException iae) {
                            return true;
                        }
                        return false;
                    })
                    .collect(ArrayList<Range>::new,
                            (list, r) -> {
                                if (list.stream().noneMatch(s -> s.script.equals(r.script))) {
                                    list.add(r);
                                }
                            },
                            ArrayList::addAll);

            newScripts.forEach(r -> {
                String fieldDesc =
                        " ".repeat(8) + "/**\n" +
                                " ".repeat(9) + "* Unicode script \"" + r.name + "\".\n" +
                                " ".repeat(9) + "* @since XX\n" +
                                " ".repeat(9) + "*/\n" +
                                " ".repeat(8) + r.script + ",\n";
                System.out.println(fieldDesc);
            });

            // aliases
            var aliases = Files.lines(Paths.get(args[0], "PropertyValueAliases.txt"))
                    .filter(l -> l.startsWith("sc ;"))
                    .map(l -> l.replaceFirst("sc ; ", ""))
                    .collect(Collectors.toList());
            newScripts.forEach(s -> aliases.stream()
                    .filter(a -> a.endsWith(s.prop))
                    .map(a -> " ".repeat(12) +
                            "aliases.put(\"" +
                            a.replaceFirst(" .*", "").toUpperCase() +
                            "\", " + s.script + ");")
                    .findAny()
                    .ifPresent(System.out::println));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static String toHexString(int cp) {
        String ret = HF.toHexDigits(cp);
        if (cp < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            return ret.substring(4);
        } else if (cp < 0x100000) {
            return ret.substring(3);
        } else if (cp <= Character.MAX_CODE_POINT) {
            return ret.substring(2);
        } else {
            throw new IllegalArgumentException("invalid code point");
        }
    }

    static class Range implements Comparable<Range> {
        int start;
        int last;
        String prop;
        String script;
        String name;

        Range (int start, int last, String script, String name) {
            this.start = start;
            this.last = last;
            this.prop = name.replaceAll(" ", "_");
            this.script =  script;
            this.name = name;
        }

        Range (String input) {
            input = input.replaceFirst("\\s#.*", "");
            start = HexFormat.fromHexDigits(input.replaceFirst("[\\s.].*", ""));
            last = input.contains("..") ?
                    HexFormat.fromHexDigits(input.replaceFirst(".*\\.\\.", "")
                            .replaceFirst(";.*", "").trim())
                    : start;
            prop = input.replaceFirst(".* ; ", "");
            script = prop.toUpperCase();
            name = prop.replaceAll("_", " ");
        }

        String printScriptStarts() {
            String startStr = toHexString(start);
            String lastStr = toHexString(last);

            return "        0x" +
                    startStr +
                    "," +
                    " ".repeat(7 - startStr.length()) +
                    "// " +
                    startStr +
                    (start != last ? ".." + lastStr + "; " : " ".repeat(lastStr.length() + 2) + "; ") +
                    script;
        }

        String printScripts() {
            String startStr = toHexString(start);
            String lastStr = toHexString(last);

            return " ".repeat(8) +
                    script +
                    "," + " ".repeat(25 - script.length()) + "// " +
                    startStr +
                    (start != last ? ".." + lastStr : "");
        }

        String printDefinitions() {
            return "/* " + name + " */";
        }

        @Override
        public int compareTo(Range other) {
            return Integer.compare(start, other.start);
        }
    }
}
