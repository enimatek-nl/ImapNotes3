/*
 * Copyright (C) 2022-2023 - Peter Korf <peter@niendo.de>
 * Copyright (C)           - kwhitefoot
 * Copyright (C)      2017 - kj
 * and Contributors.
 *
 * This file is part of ImapNotes3.
 *
 * ImapNotes3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.niendo.ImapNotes3.Miscs;

/**
 * Simple class to allow functions to return a value and a status as a single object.
 *
 * @param <T> the type parameter
 */
public class Result<T> {
    /**
     * The result can be any type, usually a String or number.
     */
    public final T result;
    /**
     * True if the result is valid, else false.
     */
    public final boolean succeeded;


    public Result(T result,
                  boolean succeeded) {
        this.result = result;
        this.succeeded = succeeded;
    }
}
