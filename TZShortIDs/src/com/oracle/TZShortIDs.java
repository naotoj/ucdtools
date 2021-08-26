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
import java.nio.file.Path;
import java.util.regex.Pattern;

// tool to generate the test method "shortTZID()" in java.time

public class TZShortIDs {

    private static final Pattern TYPE = Pattern.compile("\s*<type name=\"(?<name>[a-z0-9]+)\"\s*description=\"(?<desc>[^\"]+)\"\s*(?<depr>deprecated=\"true\")?\s*alias=\"(?<alias>[^\"]+)\".*");

    public static void main(String[] args) throws IOException {

        System.out.print(
        """
            @DataProvider(name="shortTZID")
            Object[][] shortTZID() {
                return new Object[][] {
                    // LDML's short ID, Expected Zone,
                    // Based on timezone.xml from CLDR
        """);
        Files.readAllLines(Path.of(args[0])).stream()
                .map(TYPE::matcher)
                .forEach(m -> {
                    if (m.matches()) {
                        var name = m.group("name");
                        var desc = m.group("desc");
                        var depr = m.group("depr") != null;
                        var canon = m.group("alias").split(" ");
                        if (!desc.equals("Metazone") && !depr && !name.equals("unk")) {
                            System.out.printf("""
                                                {"%s", "%s"},
                                    """, name, canon[0]);
                        }
                    }
                });
        System.out.println(
        """
                };
            }
        """);
    }
}
