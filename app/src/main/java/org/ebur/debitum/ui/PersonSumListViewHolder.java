package org.ebur.debitum.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.ebur.debitum.R;

class PersonSumListViewHolder extends RecyclerView.ViewHolder {
    private final TextView nameView;
    private final TextView oweLentLabelView;
    private final TextView sumView;

    private PersonSumListViewHolder(View itemView) {
        super(itemView);
        nameView = itemView.findViewById(R.id.list_item_name);
        oweLentLabelView = itemView.findViewById(R.id.list_item_owe_lent);
        sumView = itemView.findViewById(R.id.list_item_sum);
    }

    static PersonSumListViewHolder create(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_person_sum_list, parent, false);
        return new PersonSumListViewHolder(view);
    }

    // TODO much of the following code is replicated in TransactionListViewHolder
    //   create Superclass containing a method like formatViews(View gaveReceivedLabel, View amountView, int sign)
    //   where gaveReceivedLabel's text is set and amountView's color, based on sign
    public void bind(String name, String sum, int sign) {
        nameView.setText(name);
        sumView.setText(sum);

        if(sign == -1) {
            oweLentLabelView.setText(R.string.person_sum_list_you_owe);
            sumView.setTextColor(sumView.getResources().getColor(R.color.owe_green, null));
        }
        else if(sign == 0) {
            oweLentLabelView.setText(R.string.person_sum_list_no_debt);
            sumView.setVisibility(View.INVISIBLE);
            sumView.setHeight(0);
        }
        else { // sign == 1
            oweLentLabelView.setText(R.string.person_sum_list_you_lent);
            sumView.setTextColor(sumView.getResources().getColor(R.color.lent_red, null));
        }
    }
}
