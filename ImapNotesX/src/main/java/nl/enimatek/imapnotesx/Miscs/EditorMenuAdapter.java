/*
 * Copyright (C) 2022-2023 - Peter Korf <peter@niendo.de>
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

package nl.enimatek.imapnotesx.Miscs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import nl.enimatek.imapnotesx.NoteDetailActivity;
import nl.enimatek.imapnotesx.R;

public class EditorMenuAdapter extends ArrayAdapter<String> {
    @NonNull
    private final LayoutInflater mInflater;
    private final int mSpinnerResourceId;
    private final Context mContext;
    private final int mTextViewResourceId;
    private final NoteDetailActivity mNoteDetailActivity;


    public EditorMenuAdapter(Context context, int textViewResourceId, String[] objects,
                             int spinnerResourceId, NoteDetailActivity noteDetailActivity) {
        super(context, textViewResourceId, objects);

        mTextViewResourceId = textViewResourceId;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mSpinnerResourceId = spinnerResourceId;
        mContext = context;
        mNoteDetailActivity = noteDetailActivity;
    }

    @Override
    public View getDropDownView(int position, View convertView,
                                @NonNull ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    /**
     * Sets the icon of a row together with its content description. The entries of these popups are
     * icons without a label, so the description is the only name a screen reader can read; it
     * belongs to the entry and is therefore set here and not in layout/editor_row.xml.
     */
    private void setIcon(@NonNull ImageView icon, int drawableRes, int labelRes) {
        icon.setImageResource(drawableRes);
        icon.setContentDescription(mContext.getString(labelRes));
    }

    /**
     * Prepares the dummy spinner of a row that holds a nested popup (font size, font family). The
     * row itself deliberately keeps no id: the nested spinner is operated on its own.
     */
    private void setNestedSpinner(@NonNull View row, int spinnerId, int labelRes, int itemCount) {
        NDSpinner spinner = row.findViewById(R.id.row_spinner_dummy);
        spinner.setId(spinnerId);
        spinner.setVisibility(View.VISIBLE);
        String label = mContext.getString(labelRes);
        // The accessible name; android.widget.Spinner has no title, only the content description.
        spinner.setContentDescription(label);
        spinner.setAdapter(new EditorMenuAdapter(mContext, R.layout.editor_row,
                new String[itemCount], spinnerId, mNoteDetailActivity));
        spinner.setOnItemSelectedListener(mNoteDetailActivity);
    }

    public View getCustomView(int position, View convertView, ViewGroup parent) {
        View row = mInflater.inflate(mTextViewResourceId, parent, false);
        ImageView icon = row.findViewById(R.id.row_icon_dummy);
        switch (mSpinnerResourceId) {
            case R.id.action_format:
                switch (position) {
                    case 0:
                        row.setId(R.id.action_removeFormat);
                        setIcon(icon, R.drawable.remove_format, R.string.remove_format);
                        break;
                    case 1:
                        row.setId(R.id.action_bold);
                        setIcon(icon, R.drawable.bold, R.string.bold);
                        break;
                    case 2:
                        row.setId(R.id.action_italic);
                        setIcon(icon, R.drawable.italic, R.string.italic);
                        break;
                    case 3:
                        row.setId(R.id.action_subscript);
                        setIcon(icon, R.drawable.subscript, R.string.subscript);
                        break;
                    case 4:
                        row.setId(R.id.action_superscript);
                        setIcon(icon, R.drawable.superscript, R.string.superscript);
                        break;
                    case 5:
                        row.setId(R.id.action_strikethrough);
                        setIcon(icon, R.drawable.strikethrough, R.string.strikethrough);
                        break;
                    case 6:
                        row.setId(R.id.action_underline);
                        setIcon(icon, R.drawable.underline, R.string.underline);
                        break;
                    case 7:
                        row.setId(R.id.action_txt_color);
                        setIcon(icon, R.drawable.txt_color, R.string.txt_color);
                        break;
                    case 8:
                        row.setId(R.id.action_bg_color);
                        setIcon(icon, R.drawable.bg_color, R.string.bg_color);
                        break;
                    case 9:
                        hideIcon(icon);
                        setNestedSpinner(row, R.id.action_font_size, R.string.font_size, 7);
                        break;
                    case 10:
                        hideIcon(icon);
                        setNestedSpinner(row, R.id.action_font_family, R.string.font_family, 5);
                        break;
                }
                break;
            case R.id.action_heading:
                switch (position) {
                    case 0:
                        row.setId(R.id.action_insert_bullets);
                        setIcon(icon, R.drawable.bullets, R.string.bullets);
                        break;
                    case 1:
                        row.setId(R.id.action_insert_numbers);
                        setIcon(icon, R.drawable.numbers, R.string.numbers);
                        break;
                    case 2:
                        row.setId(R.id.action_heading1);
                        setIcon(icon, R.drawable.h1, R.string.heading1);
                        break;
                    case 3:
                        row.setId(R.id.action_heading2);
                        setIcon(icon, R.drawable.h2, R.string.heading2);
                        break;
                    case 4:
                        row.setId(R.id.action_heading3);
                        setIcon(icon, R.drawable.h3, R.string.heading3);
                        break;
                    case 5:
                        row.setId(R.id.action_heading4);
                        setIcon(icon, R.drawable.h4, R.string.heading4);
                        break;
                    case 6:
                        row.setId(R.id.action_heading5);
                        setIcon(icon, R.drawable.h5, R.string.heading5);
                        break;
                    case 7:
                        row.setId(R.id.action_heading6);
                        setIcon(icon, R.drawable.h6, R.string.heading6);
                        break;
                }
                break;
            case R.id.action_font_size:
                switch (position) {
                    case 0:
                        row.setId(R.id.action_font_size_1);
                        setIcon(icon, R.drawable.font_size_1, R.string.font_size_1);
                        break;
                    case 1:
                        row.setId(R.id.action_font_size_2);
                        setIcon(icon, R.drawable.font_size_2, R.string.font_size_2);
                        break;
                    case 2:
                        row.setId(R.id.action_font_size_3);
                        setIcon(icon, R.drawable.font_size_3, R.string.font_size_3);
                        break;
                    case 3:
                        row.setId(R.id.action_font_size_4);
                        setIcon(icon, R.drawable.font_size_4, R.string.font_size_4);
                        break;
                    case 4:
                        row.setId(R.id.action_font_size_5);
                        setIcon(icon, R.drawable.font_size_5, R.string.font_size_5);
                        break;
                    case 5:
                        row.setId(R.id.action_font_size_6);
                        setIcon(icon, R.drawable.font_size_6, R.string.font_size_6);
                        break;
                    case 6:
                        row.setId(R.id.action_font_size_7);
                        setIcon(icon, R.drawable.font_size_7, R.string.font_size_7);
                        break;
                }
                break;
            case R.id.action_font_family:
                switch (position) {
                    case 0:
                        row.setId(R.id.action_font_serif);
                        setIcon(icon, R.drawable.font_serif, R.string.font_serif);
                        break;
                    case 1:
                        row.setId(R.id.action_font_sansserif);
                        setIcon(icon, R.drawable.font_sansserif, R.string.font_sansserif);
                        break;
                    case 2:
                        row.setId(R.id.action_font_cursive);
                        setIcon(icon, R.drawable.font_cursive, R.string.font_cursive);
                        break;
                    case 3:
                        row.setId(R.id.action_font_monospace);
                        setIcon(icon, R.drawable.font_monospace, R.string.font_monospace);
                        break;
                    case 4:
                        row.setId(R.id.action_font_fantasy);
                        setIcon(icon, R.drawable.font_fantasy, R.string.font_fantasy);
                        break;
                }
                break;
            case R.id.action_alignment:
                switch (position) {
                    case 0:
                        row.setId(R.id.action_indent);
                        setIcon(icon, R.drawable.indent, R.string.indent);
                        break;
                    case 1:
                        row.setId(R.id.action_outdent);
                        setIcon(icon, R.drawable.outdent, R.string.outdent);
                        break;
                    case 2:
                        row.setId(R.id.action_align_left);
                        setIcon(icon, R.drawable.justify_left, R.string.align_left);
                        break;
                    case 3:
                        row.setId(R.id.action_align_center);
                        setIcon(icon, R.drawable.justify_center, R.string.align_center);
                        break;
                    case 4:
                        row.setId(R.id.action_align_right);
                        setIcon(icon, R.drawable.justify_right, R.string.align_right);
                        break;
                    case 5:
                        row.setId(R.id.action_blockquote);
                        setIcon(icon, R.drawable.blockquote, R.string.blockquote);
                        break;
                    case 6:
                        row.setId(R.id.action_pre);
                        setIcon(icon, R.drawable.action_pre, R.string.pre_formatted);
                        break;
                    case 7:
                        row.setId(R.id.action_code_html);
                        setIcon(icon, R.drawable.code_html, R.string.code_html);
                        break;
                    case 8:
                        row.setId(R.id.action_code_off_html);
                        setIcon(icon, R.drawable.code_off_html, R.string.code_off_html);
                        break;
                }
                break;
            case R.id.action_insert:
                switch (position) {
                    case 0:
                        row.setId(R.id.action_insert_checkbox);
                        setIcon(icon, R.drawable.checkbox, R.string.checkbox);
                        break;
                    case 1:
                        row.setId(R.id.action_insert_image);
                        setIcon(icon, R.drawable.insert_image, R.string.image);
                        break;
                    case 2:
                        row.setId(R.id.action_insert_audio);
                        setIcon(icon, R.drawable.insert_audio, R.string.audio_stream);
                        break;
                    case 3:
                        row.setId(R.id.action_insert_video);
                        setIcon(icon, R.drawable.insert_video, R.string.video);
                        break;
                    case 4:
                        row.setId(R.id.action_insert_youtube);
                        setIcon(icon, R.drawable.insert_youtube, R.string.youtube_video);
                        break;
                    case 5:
                        row.setId(R.id.action_insert_link);
                        setIcon(icon, R.drawable.insert_link, R.string.link);
                        break;
                    case 6:
                        row.setId(R.id.action_insert_star);
                        setIcon(icon, R.drawable.star, R.string.star_symbol);
                        break;
                    case 7:
                        row.setId(R.id.action_insert_exclamation);
                        setIcon(icon, R.drawable.exclamation, R.string.exclamation_symbol);
                        break;
                    case 8:
                        row.setId(R.id.action_insert_question);
                        setIcon(icon, R.drawable.question, R.string.question_symbol);
                        break;
                    case 9:
                        row.setId(R.id.action_insert_hline);
                        setIcon(icon, R.drawable.hline, R.string.horizontal_line);
                        break;
                    case 10:
                        row.setId(R.id.action_insert_section);
                        setIcon(icon, R.drawable.insert_section, R.string.section);
                        break;
                    case 11:
                        row.setId(R.id.action_insert_datetime);
                        setIcon(icon, R.drawable.insert_datetime, R.string.date_time);
                        break;
                    case 12:
                        row.setId(R.id.action_insert_hashtag);
                        setIcon(icon, R.drawable.insert_hashtag, R.string.hashtag);
                        break;
                }
                break;
            case R.id.action_table:
                switch (position) {
                    case 0:
                        row.setId(R.id.action_insert_table);
                        setIcon(icon, R.drawable.insert_table_2x2, R.string.insert_table);
                        break;
                    case 1:
                        row.setId(R.id.action_insert_column);
                        setIcon(icon, R.drawable.insert_column, R.string.insert_column);
                        break;
                    case 2:
                        row.setId(R.id.action_insert_row);
                        setIcon(icon, R.drawable.insert_row, R.string.insert_row);
                        break;
                    case 3:
                        row.setId(R.id.action_delete_column);
                        setIcon(icon, R.drawable.delete_column, R.string.delete_column);
                        break;
                    case 4:
                        row.setId(R.id.action_delete_row);
                        setIcon(icon, R.drawable.delete_row, R.string.delete_row);
                        break;
                }
                break;
        }
        return row;
    }

    /**
     * A row whose entry is a nested spinner has no icon of its own; the invisible placeholder would
     * still be read by a screen reader if it kept a description.
     */
    private void hideIcon(@NonNull ImageView icon) {
        icon.setImageResource(0);
        icon.setVisibility(View.INVISIBLE);
        icon.setPadding(0, 0, 0, 0);
        icon.setContentDescription(null);
    }
}
