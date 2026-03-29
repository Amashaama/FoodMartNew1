package lk.sa.foodmart.fragment;

import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.renderscript.ScriptGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.firebase.Firebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.checkerframework.checker.units.qual.A;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lk.sa.foodmart.R;
import lk.sa.foodmart.activity.MainActivity;
import lk.sa.foodmart.adapter.SelectedPhotoAdapter;
import lk.sa.foodmart.databinding.FragmentSellerAddItemBinding;

public class SellerAddItemFragment extends Fragment {

  private FragmentSellerAddItemBinding binding;
  private FirebaseAuth auth;
  private FirebaseFirestore firestore;
  private FirebaseStorage storage;
  private SelectedPhotoAdapter selectedPhotoAdapter;

  private ArrayList<Uri> selectedImageUris = new ArrayList<>();

  private ActivityResultLauncher<String> imagePickerLauncher;

  private Uri mainImageUri;

  private boolean isImagePickerOpen = false;

    private String sellerAddress = "";
    private String sellerCity = "";
    private double sellerLatitude = 0.0;
    private double sellerLongitude = 0.0;


    private final ArrayList<String> categoryNames = new ArrayList<>();
    private final ArrayList<String> categoryIds=new ArrayList<>();

    private String selectedCategoryId = "";
    private String sellerName="";

    private boolean isEditMode = false;
    private String editingItemId = "";

    @Override
  public View onCreateView(
      @NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    binding = FragmentSellerAddItemBinding.inflate(inflater, container, false);

    auth = FirebaseAuth.getInstance();
    firestore = FirebaseFirestore.getInstance();
    storage = FirebaseStorage.getInstance();

    if(getArguments() != null){
        isEditMode = getArguments().getBoolean("isEdit",false);
        editingItemId = getArguments().getString("itemId","");
    }

    if(isEditMode){
        binding.tvItemTitle.setText("Edit Food Item");
        binding.btnSave.setText("Update Item");
        loadItemForEdit();
    }else{
        binding.tvItemTitle.setText("Add New Food Item");
        binding.btnSave.setText("Save Item");
    }

    loadCategories();
    setupRecyclerView();
    setupImagePicker();
    setupListeners();
    loadSellerLocationDate();
    setupErrorClearListeners();

    return binding.getRoot();
  }

    private void setupErrorClearListeners() {
        addTextWatcher(binding.etItemName,()->binding.tilItemName.setError(null));
        addTextWatcher(binding.etDescription,()->binding.tilDescription.setError(null));
        addTextWatcher(binding.etBasePrice,()->binding.tilBasePrice.setError(null));
        addTextWatcher(binding.etQuantity,()->binding.tilQuantity.setError(null));
        addTextWatcher(binding.actCategory,()->binding.tilCategory.setError(null));

    }

    private void addTextWatcher(android.widget.TextView textView,Runnable onTextChanged){
        textView.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                onTextChanged.run();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }
        });
    }

    private void setupListeners() {
    binding.cardMainImage.setOnClickListener(
        v -> {
          if (isImagePickerOpen) return;

          isImagePickerOpen = true;
          imagePickerLauncher.launch("image/*");
        });

    binding.btnSave.setOnClickListener(v -> {
        validateAndSaveItem();
    });
  }

  private void setupImagePicker() {

    imagePickerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> {
              isImagePickerOpen = false;
              if (uris != null && !uris.isEmpty()) {
                int availableSlots = 3 - selectedImageUris.size();

                if (availableSlots <= 0) {
                  Toast.makeText(getContext(), "Maximum 3 images allowed", Toast.LENGTH_SHORT)
                      .show();
                  return;
                }

                int countToAdd = Math.min(uris.size(), availableSlots);

                for (int i = 0; i < countToAdd; i++) {
                  Uri newUri = uris.get(i);
                  mainImageUri = newUri;
                  if (!selectedImageUris.contains(newUri)) {
                    selectedImageUris.add(newUri);
                  }
                }

                updatePhotoUI();
              }
            });
  }

  private void setupRecyclerView() {

    binding.rvPhotos.setLayoutManager(
        new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
    selectedPhotoAdapter =
        new SelectedPhotoAdapter(
            selectedImageUris,
            position -> {
              if (position >= 0 && position < selectedImageUris.size()) {
                selectedImageUris.remove(position);
                selectedPhotoAdapter.notifyDataSetChanged();
                updatePhotoUI();
              }
            });
    binding.rvPhotos.setAdapter(selectedPhotoAdapter);
  }

  private void updatePhotoUI() {
    binding.tvPhotoCount.setText(selectedImageUris.size() + " /3 Images");
    if (!selectedImageUris.isEmpty()) {
      binding.ivMainPhoto.setVisibility(View.VISIBLE);
      binding.layoutUploadPlaceholder.setVisibility(View.GONE);
      binding.ivPhotoAddImage.setVisibility(View.VISIBLE);
      binding.ivMainPhoto.setImageURI(mainImageUri);
      binding.rvPhotos.setVisibility(View.VISIBLE);

    } else {
      binding.ivMainPhoto.setVisibility(View.GONE);
      binding.layoutUploadPlaceholder.setVisibility(View.VISIBLE);
      binding.ivPhotoAddImage.setVisibility(View.GONE);
    }

    if (selectedPhotoAdapter != null) {
      selectedPhotoAdapter.notifyDataSetChanged();
    }
  }

  private void loadSellerLocationDate(){
      if(auth.getCurrentUser() == null)return;

      String sellerId = auth.getCurrentUser().getUid();

      firestore.collection("users")
              .document(sellerId)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  sellerAddress = documentSnapshot.getString("address") != null
                          ? documentSnapshot.getString("address") : "";

                  sellerCity=documentSnapshot.getString("city") != null
                          ? documentSnapshot.getString("city") : "";


                  Double lat= documentSnapshot.getDouble("latitude");
                  Double lng=documentSnapshot.getDouble("longitude");

                  if(lat != null) sellerLatitude =lat;
                  if(lng != null) sellerLongitude =lng;

                  sellerName = documentSnapshot.getString("name");

              })
              .addOnFailureListener(e->{
                 Toast.makeText(getContext(),"Failed to load seller location",Toast.LENGTH_SHORT).show();
              });
  }


  private void loadCategories(){
        categoryNames.clear();
        categoryIds.clear();

        firestore.collection("categories")
                .whereEqualTo("active",true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for(QueryDocumentSnapshot document : queryDocumentSnapshots){
                        String id = document.getId();
                        String name= document.getString("name");

                        if(name != null){
                            categoryIds.add(id);
                            categoryNames.add(name);
                        }
                    }

                    ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                            getContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            categoryNames
                    );

                    binding.actCategory.setAdapter(categoryAdapter);
                    binding.actCategory.setOnItemClickListener(((parent,view,position,id) -> {
                        selectedCategoryId=categoryIds.get(position);
                        binding.actCategory.setText(categoryNames.get(position),false);
                        binding.tilCategory.setError(null);
                    }));

                }).addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to load categories", Toast.LENGTH_SHORT).show();
                });
  }

  private void validateAndSaveItem(){

        clearErrors();

      String itemName = binding.etItemName.getText().toString().trim();
      String description = binding.etDescription.getText().toString().trim();
      String categoryName = binding.actCategory.getText().toString().trim();
      String basePriceStr = binding.etBasePrice.getText().toString().trim();
      String quantityStr = binding.etQuantity.getText().toString().trim();
      String portionSize = getSelectedPortionSize();

      boolean available = true;

      boolean valid = true;

      if(itemName.isEmpty()){
          binding.tilItemName.setError("Item name is required");
          valid = false;
      }

      if(description.isEmpty()){
          binding.tilDescription.setError("Description is required");
          valid = false;
      }

      if(categoryName.isEmpty() || selectedCategoryId.isEmpty()){

          binding.tilCategory.setError("Please select a category");
          valid = false;

      }

      if(basePriceStr.isEmpty()){
          binding.tilBasePrice.setError("Price is required");
          valid = false;
      }

      if(quantityStr.isEmpty()){
          binding.tilQuantity.setError("Quantity is required");
          valid = false;
      }

      if(portionSize.isEmpty()){
          Toast.makeText(requireContext(), "Please select a portion size", Toast.LENGTH_SHORT).show();
          valid = false;
      }

      if(selectedImageUris.isEmpty()){
          Toast.makeText(requireContext(), "Please select at least one image", Toast.LENGTH_SHORT).show();
          valid = false;
      }

      if(!valid) return;

      double itemPrice;
      int quantity;

      try{
          itemPrice = Double.parseDouble(basePriceStr);
          quantity=Integer.parseInt(quantityStr);
      }catch (Exception e){
          Toast.makeText(requireContext(), "Invalid price or quantity", Toast.LENGTH_SHORT).show();
          return;
      }

      saveItem(itemName, description, categoryName, itemPrice, quantity, portionSize, available);




  }

    private void saveItem(String itemName, String description, String categoryName, double itemPrice, int quantity, String portionSize, boolean available) {

        if(auth.getCurrentUser() == null){
            Toast.makeText(getContext(),"User not logged in",Toast.LENGTH_SHORT).show();
        }

        String sellerId = auth.getCurrentUser().getUid();

        DocumentReference itemRef ;
        String itemId;

        if(isEditMode && editingItemId != null && !editingItemId.isEmpty()){
            itemRef = firestore.collection("food_items").document(editingItemId);
            itemId = editingItemId;
        }else{

        itemRef = firestore.collection("food_items").document();
        itemId= itemRef.getId();

        }

        uploadImagesAndSaveData(
                itemRef,
                itemId,
                sellerId,
                sellerName,
                itemName,
                description,
                categoryName,
                itemPrice,
                quantity,
                portionSize,
                available
        );

    }

    private void uploadImagesAndSaveData(DocumentReference itemRef, String itemId, String sellerId,String sellerName ,String itemName, String description, String categoryName, double itemPrice, int quantity, String portionSize, boolean available) {
        List<String> imageUris = new ArrayList<>();
        final int totalImages = selectedImageUris.size();
        final int[] uploadedCount ={0};

        for(int i=0; i< selectedImageUris.size();i++){
            Uri imageUri = selectedImageUris.get(i);

            StorageReference imageRef= storage.getReference()
                    .child("item_images")
                    .child(sellerId)
                    .child(itemId)
                    .child("image_"+i+".jpg");

            imageRef.putFile(imageUri)
                    .continueWithTask(task -> {
                        if(!task.isSuccessful()){
                            throw task.getException();
                        }

                        return imageRef.getDownloadUrl();
                    })
                    .addOnSuccessListener(uri -> {
                        imageUris.add(uri.toString());
                        uploadedCount[0] ++;

                        if(uploadedCount[0] == totalImages){
                            saveItemDataToFirestore(
                              itemRef,
                              itemId,
                              sellerId,
                              itemName,
                              description,
                              categoryName,
                              itemPrice,
                              quantity,
                              portionSize,
                              available,
                              imageUris
                            );
                        }
                    }).addOnFailureListener(e -> {

                    });
        }

    }

    private void saveItemDataToFirestore(DocumentReference itemRef, String itemId, String sellerId, String itemName, String description, String categoryName, double itemPrice, int quantity, String portionSize, boolean available, List<String> imageUris) {

        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("itemId",itemId);
        itemMap.put("sellerId",sellerId);
        itemMap.put("itemName",itemName);
        itemMap.put("itemDescription",description);
        itemMap.put("categoryId",selectedCategoryId);
        itemMap.put("categoryName",categoryName);
        itemMap.put("itemPrice",itemPrice);
        itemMap.put("quantity",quantity);
        itemMap.put("portionSize",portionSize);
        itemMap.put("available",available);
        itemMap.put("imageUris",imageUris);

        itemMap.put("sellerAddress",sellerAddress);
        itemMap.put("sellerCity",sellerCity);
        itemMap.put("sellerLatitude",sellerLatitude);
        itemMap.put("sellerLongitude",sellerLongitude);

        itemMap.put("createdAt",System.currentTimeMillis());

        itemRef.set(itemMap)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(requireContext(), "Item saved successfully", Toast.LENGTH_SHORT).show();
                    clearForm();
                   if(requireActivity() instanceof MainActivity){
                       ((MainActivity) requireActivity()).navigateToFragment(new SellerMyShopFragment(),R.id.bottom_nav_my_shop,R.id.side_nav_seller_my_shop,false);
                   }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to save item: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void clearForm() {
        binding.etItemName.setText("");
        binding.etDescription.setText("");
        binding.actCategory.setText("");
        binding.etBasePrice.setText("");
        binding.etQuantity.setText("");

        binding.rgPortionSize.clearCheck();

        selectedCategoryId = "";

        selectedImageUris.clear();
        mainImageUri = null;
        updatePhotoUI();
    }

    private void clearErrors() {
        binding.tilItemName.setError(null);
        binding.tilDescription.setError(null);
        binding.tilCategory.setError(null);
        binding.tilBasePrice.setError(null);
        binding.tilQuantity.setError(null);
    }

    private String getSelectedPortionSize() {
        int selectedId = binding.rgPortionSize.getCheckedRadioButtonId();

        if(selectedId == binding.rbRegular.getId()){
            return "Regular";
        }else if(selectedId ==binding.rbSmall.getId()){
            return "Small";

        }else if(selectedId==binding.rbLarge.getId()){
            return "Large";

        }else if(selectedId == binding.rbFamily.getId()){
            return "Family";

        }

        return "";
    }

    private void loadItemForEdit(){

        if(editingItemId == null || editingItemId.isEmpty()) return;

        firestore.collection("food_items")
                .document(editingItemId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if(documentSnapshot.exists()){
                        String itemName = documentSnapshot.getString("itemName");
                        String description = documentSnapshot.getString("itemDescription");
                        String categoryName = documentSnapshot.getString("categoryName");
                        Double price = documentSnapshot.getDouble("itemPrice");
                        Long quantity = documentSnapshot.getLong("quantity");
                        String portionSize = documentSnapshot.getString("portionSize");
                        Boolean available = documentSnapshot.getBoolean("available");

                        binding.etItemName.setText(itemName != null ? itemName: "");
                        binding.etDescription.setText(description != null ? description : "");
                        binding.actCategory.setText(categoryName != null ? categoryName : "", false);
                        binding.etBasePrice.setText(price != null ? String.valueOf(price) : "");
                        binding.etQuantity.setText(quantity != null ? String.valueOf(quantity) : "");


                        selectedCategoryId = documentSnapshot.getString("categoryId") != null ? documentSnapshot.getString("categoryId"): "";

                        if(portionSize != null){
                            switch (portionSize){
                                case "Small":
                                    binding.rbSmall.setChecked(true);
                                    break;
                                case "Regular":
                                    binding.rbRegular.setChecked(true);
                                    break;
                                case "Large":
                                    binding.rbLarge.setChecked(true);
                                    break;
                                case "Family":
                                    binding.rbFamily.setChecked(true);
                                    break;

                            }

                        }

                        List<String> existingImageUris = (List<String>) documentSnapshot.get("imageUris");
                        if(existingImageUris != null && !existingImageUris.isEmpty()){
                            Glide.with(requireActivity())
                                    .load(existingImageUris.get(0))
                                    .into(binding.ivMainPhoto);

                            binding.ivMainPhoto.setVisibility(View.VISIBLE);
                            binding.layoutUploadPlaceholder.setVisibility(View.GONE);
                            binding.ivPhotoAddImage.setVisibility(View.VISIBLE);
                            binding.tvPhotoCount.setText(existingImageUris.size()+" /3 Images");

                        }


                    }
                }).addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Failed to load item for edit", Toast.LENGTH_SHORT).show()
                );

    }


}
