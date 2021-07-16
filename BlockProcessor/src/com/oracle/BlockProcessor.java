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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BlockProcessor {

    // arg[0] is the unicodedata directory
    public static void main(String[] args) {
        final var last = new Block[1]; // last script range

        try {
            var blocks = Files.lines(Paths.get(args[0], "Blocks.txt"))
                    .filter(Predicate.not(l -> l.startsWith("#") || l.isBlank()))
                    .map(Block::new)
                    .flatMap(b -> {
                        Stream<Block> flat;
                        if (last[0] != null && last[0].last != b.start - 1) {
                            flat = Stream.of(new Block(
                                    toHexString(last[0].last + 1) + ".." +
                                            toHexString(b.start - 1) + "; unassigned"
                            ), b);
                        } else {
                            flat = Stream.of(b);
                        }
                        last[0] = b;
                        return flat;
                    })
                    .collect(Collectors.toList());

            // blockStarts
            blocks.stream()
                    .map(Block::blockStarts)
                    .forEach(System.out::println);

            // blocks
            blocks.stream()
                    .map(Block::blocks)
                    .forEach(System.out::println);

            // constants
            var newBlocks = blocks.stream()
                    .filter(b -> {
                        try {
                            Character.UnicodeBlock.forName(b.blockNameUnderscore);
                        } catch (IllegalArgumentException iae) {
                            return true;
                        }
                        return false;
                    })
                    .filter(b -> b.assigned)
                    .collect(ArrayList<Block>::new,
                            (list, b) -> {
                                if (list.stream().noneMatch(s -> s.blockName.equals(b.blockName))) {
                                    list.add(b);
                                }
                            },
                            ArrayList::addAll);

            newBlocks.forEach(b -> {
                String fieldDesc =
                        " ".repeat(8) + "/**\n" +
                                " ".repeat(9) + "* Constant for the \"" + b.blockName + "\" Unicode\n" +
                                " ".repeat(9) + "* character block.\n" +
                                " ".repeat(9) + "* @since XX\n" +
                                " ".repeat(9) + "*/\n" +
                                " ".repeat(8) + "public static final UnicodeBlock " +
                                b.blockNameUnderscore + " =\n" +
                                " ".repeat(12) + "new UnicodeBlock(\"" + b.blockNameUnderscore + "\"" +
                                (!b.blockNameUnderscore.equals(b.blockNameSpace) ? ",\n" +
                                    " ".repeat(29) + "\"" + b.blockNameSpace + "\"" : "") +
                                (!b.blockNameUnderscore.equals(b.blockNameNoSpace) ? ",\n" +
                                    " ".repeat(29) + "\"" + b.blockNameNoSpace + "\"" : "") +
                                ");\n";

                System.out.println(fieldDesc);
            });
/*
            // aliases
            var aliases = Files.lines(Paths.get(args[0], "PropertyValueAliases.txt"))
                    .filter(l -> l.startsWith("sc ;"))
                    .map(l -> l.replaceFirst("sc ; ", ""))
                    .collect(Collectors.toList());
            newScripts.stream()
                    .forEach(s -> {
                        aliases.stream()
                                .filter(a -> a.endsWith(s.prop))
                                .map(a -> " ".repeat(12) +
                                        "aliases.put(\"" +
                                        a.replaceFirst(" .*", "").toUpperCase() +
                                        "\", " + s.script + ");")
                                .findAny()
                                .ifPresent(System.out::println);
                    });
*/
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static int toInt(String hexStr) {
        return Integer.parseUnsignedInt(hexStr, 16);
    }

    static String toHexString(int cp) {
        String ret = Integer.toUnsignedString(cp, 16).toUpperCase();
        if (ret.length() < 4) {
            ret = "0".repeat(4 - ret.length()) + ret;
        }
        return ret;
    }

    static class Block {
        int start;
        int last;
        String prop;
        String blockName;
        String blockNameSpace;
        String blockNameUnderscore;
        String blockNameNoSpace;
        boolean assigned;

        Block (String prop) {
            this.prop = prop;
            start = toInt(prop.replaceFirst("\\.\\..*", ""));
            last = toInt(prop.replaceFirst(".*\\.\\.", "")
                            .replaceFirst(";.*", "").trim());
            blockName = prop.replaceFirst(".*; ", "").trim();
            blockNameSpace = blockName.toUpperCase();
            blockNameUnderscore = blockNameSpace.replaceAll("[ -]", "_");
            blockNameNoSpace = blockNameSpace.replaceAll(" ", "");
            assigned = !blockName.equals("unassigned");

            // compatibility handling
            switch (blockNameUnderscore) {
                case "GREEK_AND_COPTIC":
                    blockNameUnderscore = "GREEK";
                    break;
                case "CYRILLIC_SUPPLEMENT":
                    blockNameUnderscore = "CYRILLIC_SUPPLEMENTARY";
                    break;
                case "COMBINING_DIACRITICAL_MARKS_FOR_SYMBOLS":
                    blockNameUnderscore ="COMBINING_MARKS_FOR_SYMBOLS";
                    break;
            }
        }

        String blockStarts() {
            String startStr = toHexString(start);

            return "        0x" +
                    startStr +
                    "," +
                    " ".repeat(7 - startStr.length()) +
                    "// " +
                    (assigned ? prop: " ".repeat(startStr.length() * 2 + 4) + "unassigned");
        }

        String blocks() {

            return " ".repeat(8) +
                    (assigned ? blockNameUnderscore : "null") +
                    ",";
        }
    }
}

