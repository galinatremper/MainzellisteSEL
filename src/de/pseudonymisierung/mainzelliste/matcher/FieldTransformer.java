/*
 * Copyright (C) 2013-2015 Martin Lablans, Andreas Borg, Frank Ückert
 * Contact: info@mainzelliste.de
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with Jersey (https://jersey.java.net) (or a modified version of that
 * library), containing parts covered by the terms of the General Public
 * License, version 2.0, the licensors of this Program grant you additional
 * permission to convey the resulting work.
 */
package de.pseudonymisierung.mainzelliste.matcher;

import java.util.Vector;

import de.pseudonymisierung.mainzelliste.CompoundField;
import de.pseudonymisierung.mainzelliste.Field;

/**
 * A FieldTransformer is the abstraction of a transformation of one
 * {@link Field} into another, for example converting a string to upper case or
 * generating phonetic code. The type parameters specify which field types are
 * suitable for input and which field type is produced as output.
 *
 * @param <IN>
 *            Class of fields suitable as input field.
 * @param <OUT>
 *            Class of output fields generated by a specific FieldTransformer.
 */
public abstract class FieldTransformer<IN extends Field<?>, OUT extends Field<?>>{

    /**
     * Transform a field.
     * @param input The input field.
     * @return The transformed field.
     */
    public abstract OUT transform(IN input);

    /**
     * Return the class suitable for input fields. Implementations should return
     * the a class object corresponding to type parameter IN. This method is
     * needed to check at runtime if a specific FieldTransformer object is
     * compatible to a specific Field object (type parameters cannot be queried
     * at runtime).
     *
     * @return Class object corresponding to type parameter IN.
     */
    public abstract Class<IN> getInputClass();

    /**
     * Return the class of output fields. See {@link #getInputClass()} for
     * details.
     *
     * @return Class object corresponding to type parameter OUT.
     */
    public abstract Class<OUT> getOutputClass();

    /**
     * Default handling of compound fields: Element-wise transformation of the
     * components.
     *
     * @param input
     *            A CompoundField.
     * @return A CompoundField where component i is the result of
     *         this.transform(input.getValueAt(i)).
     */
    public CompoundField<OUT> transform(CompoundField<IN> input)
    {
        Vector<OUT> outFields = new Vector<OUT>(input.getSize());
        for (IN thisField : input.getValue())
        {
            outFields.add(this.transform(thisField));
        }
        CompoundField<OUT> result = new CompoundField<OUT>(outFields);
        return result;
    }
}
