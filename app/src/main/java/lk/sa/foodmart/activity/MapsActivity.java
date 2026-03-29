package lk.sa.foodmart.activity;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import lk.sa.foodmart.R;
import lk.sa.foodmart.databinding.ActivityMapsBinding;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

  private GoogleMap mMap;
  private ActivityMapsBinding binding;

  private LatLng selectedLatLang;
  private String selectedAddress = "";
  private String selectedAddressCity = "";

  private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;

  private FusedLocationProviderClient fusedLocationProviderClient;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    binding = ActivityMapsBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

    // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    SupportMapFragment mapFragment =
        (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
    if (mapFragment != null) {

      mapFragment.getMapAsync(this);
    }

    binding.btnConfirmLocation.setOnClickListener(
        v -> {
          if (selectedLatLang != null) {
            Intent intent = new Intent();
            intent.putExtra("latitude", selectedLatLang.latitude);
            intent.putExtra("longitude", selectedLatLang.longitude);
            intent.putExtra("address", selectedAddress);
            intent.putExtra("city", selectedAddressCity);
            setResult(RESULT_OK, intent);
            finish();

          } else {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show();
          }
        });
  }

  @Override
  public void onMapReady(GoogleMap googleMap) {
    mMap = googleMap;

    mMap.getUiSettings().setZoomControlsEnabled(true);
    mMap.getUiSettings().setCompassEnabled(true);

    mMap.setOnMapLongClickListener(
        latLng -> {
          selectedLatLang = latLng;
          mMap.clear();
          mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
          mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
          selectedAddress = getAddressFromLatLng(latLng.latitude, latLng.longitude);
          selectedAddressCity =
              getCityFromLatLng(selectedLatLang.latitude, selectedLatLang.longitude);
        });

    enableCurrentLocation();
  }

  private void enableCurrentLocation() {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(
          this,
          new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
          LOCATION_PERMISSION_REQUEST_CODE);

      return;
    }

    if (mMap != null) {
      mMap.setMyLocationEnabled(true);

      fusedLocationProviderClient
          .getLastLocation()
          .addOnSuccessListener(
              this,
              location -> {
                if (location != null) {
                  selectedLatLang = new LatLng(location.getLatitude(), location.getLongitude());
                  selectedAddress =
                      getAddressFromLatLng(selectedLatLang.latitude, selectedLatLang.longitude);
                  selectedAddressCity =
                      getCityFromLatLng(selectedLatLang.latitude, selectedLatLang.longitude);
                  mMap.clear();
                  mMap.addMarker(
                      new MarkerOptions().position(selectedLatLang).title("Current Location"));
                  mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLang, 15f));
                }
              });
    }
  }

  private String getAddressFromLatLng(double lat, double lng) {

    Geocoder geocoder = new Geocoder(this, Locale.getDefault());

    try {
      List<Address> addressList = geocoder.getFromLocation(lat, lng, 1);
      if (addressList != null && !addressList.isEmpty()) {
        return addressList.get(0).getAddressLine(0);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return "Unknown address";
  }

  private String getCityFromLatLng(double lat, double lng) {
    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
    try {
      List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
      if (addresses != null && !addresses.isEmpty()) {
        return addresses.get(0).getLocality();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return "no city";
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        enableCurrentLocation();
      } else {
        Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
      }
    }
  }
}
