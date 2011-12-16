package org.ovirt.engine.ui.webadmin.widget.table.column;

import com.google.gwt.user.cellview.client.Column;

/**
 * Column for displaying text using {@link TextCellWithTooltip}.
 * 
 * @param <T>
 *            Table row data type.
 */
public abstract class TextColumnWithTooltip<T> extends Column<T, String> {

    public TextColumnWithTooltip() {
        this(TextCellWithTooltip.UNLIMITED_LENGTH);
    }

    public TextColumnWithTooltip(int maxTextLength) {
        super(new TextCellWithTooltip(maxTextLength));
    }

}
