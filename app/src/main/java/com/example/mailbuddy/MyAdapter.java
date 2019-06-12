package com.example.mailbuddy;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {
    private List<ListItem> listItems;
    private Context context;


    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class MyViewHolder extends RecyclerView.ViewHolder {
        private TextView textViewHeader;
        private TextView textViewDesc;

        // each data item is just a string in this case
        public TextView textView;
        public MyViewHolder(View v) {
            super(v);

            textViewHeader = (TextView) v.findViewById(R.id.listItemHeader);
            textViewDesc = (TextView) v.findViewById(R.id.listItemDesc);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public MyAdapter(List<ListItem> listItems, Context context) {
        this.listItems = listItems;
        this.context = context;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public MyAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent,
                                                     int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item, parent, false);
        MyViewHolder vh = new MyViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        ListItem listItem = listItems.get(position);
        holder.textViewHeader.setText(listItem.getHeader());
        holder.textViewDesc.setText(listItem.getDesc());

    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return listItems.size();
    }

    public ListItem getDevice(int position){
        return listItems.get(position);
    }
}
