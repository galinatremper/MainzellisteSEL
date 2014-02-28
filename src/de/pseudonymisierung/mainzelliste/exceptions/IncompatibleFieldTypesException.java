/*
 * Copyright (C) 2013 Martin Lablans, Andreas Borg, Frank Ückert
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
package de.pseudonymisierung.mainzelliste.exceptions;

import de.pseudonymisierung.mainzelliste.Field;
import de.pseudonymisierung.mainzelliste.matcher.FieldTransformer;

/** 
 * Exception for the case that an FieldTransformer and a Field or two chained FieldTransformers
 * are not compatible respective to their type parameters.
 */
public class IncompatibleFieldTypesException extends Exception {

	public IncompatibleFieldTypesException(FieldTransformer<Field<?>, Field<?>> first,
			FieldTransformer<Field<?>, Field<?>> second)
	{
		super("Output class of " + first.getClass() +
				" does not match input class of " + second.getClass());
	}

	public IncompatibleFieldTypesException(Field<?> input,
			FieldTransformer<Field<?>, Field<?>> transformer)
	{
		super("Field class " + input.getClass() +
				" does not match input class of " + transformer.getClass());
	}

	public IncompatibleFieldTypesException(FieldTransformer<Field<?>, Field<?>> transformer,
			Field<?> output)
	{
		super("Output class of " + transformer.getClass() +
				" does not match field class " + output.getClass());
	}
}