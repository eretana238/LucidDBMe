/*
// $Id$
// Farrago is an extensible data management system.
// Copyright (C) 2008-2008 Disruptive Tech
// Copyright (C) 2008-2008 The Eigenbase Project
//
// This program is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 2 of the License, or (at your option)
// any later version approved by The Eigenbase Project.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package com.disruptivetech.farrago.calc;

/**
 * Enumeration of aggregate operations to be performed on aggregation buckets
 * for windowed or streaming aggregation.
 * 
 * @author Jack Hahn
 * @version $Id:
 *          //open/dt/dev/farrago/src/com/disruptivetech/farrago/calc/AggOp.java#1 $
 * @since Feb 5, 2004
 */
public enum AggOp {
	None,
	/**
	 * Initialize bucket to zero or null values
	 */
	Init,
	/**
	 * Update bucket in response to new row.
	 */
	Add,
	/**
	 * Update bucket in response to row leaving window.
	 */
	Drop,
	/**
	 * Initialize bucket and update for new row.
	 */
	InitAdd
}