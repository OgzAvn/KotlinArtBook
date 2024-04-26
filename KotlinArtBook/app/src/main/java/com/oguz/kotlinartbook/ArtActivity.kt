package com.oguz.kotlinartbook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.oguz.kotlinartbook.databinding.ActivityArtBinding
import java.io.ByteArrayOutputStream
import java.io.IOException

class ArtActivity : AppCompatActivity() {

    private lateinit var binding : ActivityArtBinding
    private lateinit var activityResultLauncher : ActivityResultLauncher<Intent>
    private lateinit var permissionLauncer : ActivityResultLauncher<String>
    var selectedBitMap : Bitmap? = null
    private lateinit var database : SQLiteDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArtBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        database = this.openOrCreateDatabase("Arts", MODE_PRIVATE,null)

        registerLauncher()

        val intent = intent

        val info  = intent.getStringExtra("info")

        if (info.equals("new")){

            binding.artistText.setText(" ")
            binding.nameText.setText(" ")
            binding.yearText.setText(" ")
            binding.button.visibility = View.VISIBLE

            val selectedImageBackGround = BitmapFactory.decodeResource(applicationContext.resources,R.drawable.selectimage)
            binding.imageView.setImageBitmap(selectedImageBackGround)



        }else{
            binding.button.visibility = View.INVISIBLE

            val selectedId = intent.getIntExtra("id",1)

            val cursor = database.rawQuery("SELECT * FROM arts WHERE id = ?", arrayOf(selectedId.toString()))

            val artNameIx = cursor.getColumnIndex("artname")
            val artistNameIx = cursor.getColumnIndex("artistname")
            val yearIx = cursor.getColumnIndex("year")
            val imageIx = cursor.getColumnIndex("image")

            while (cursor.moveToNext()){

                binding.nameText.setText(cursor.getString(artNameIx))
                binding.artistText.setText(cursor.getString(artistNameIx))
                binding.yearText.setText(cursor.getString(yearIx))

                val byteArray = cursor.getBlob(imageIx)
                val bitmap = BitmapFactory.decodeByteArray(byteArray,0,byteArray.size)
                binding.imageView.setImageBitmap(bitmap)
            }

            cursor.close()
        }
    }


    fun save(view : View){

        val artName = binding.nameText.text.toString()
        val artistName = binding.artistText.text.toString()
        val year = binding.yearText.text.toString()

        if (selectedBitMap != null && artName != " " && artistName != " "){

            val smallBitmap = makesmallerBitmap(selectedBitMap!!,300)


            //Görseli veriye çeviriyoruz
            //eğer kullanıcıya göstermek istiyorsan bitmap yapmamız lazım, eğer database'de saklamak istiyorsan bytearray saklamamız lazım.
            val outputStream = ByteArrayOutputStream()
            smallBitmap.compress(Bitmap.CompressFormat.PNG,50,outputStream)
            val byteArray = outputStream.toByteArray()

            try {

                database.execSQL("CREATE TABLE IF NOT EXISTS arts (id INTEGER PRIMARY KEY, artname VARCHAR ,artistname VARCHAR ,year VARCHAR,image BLOB)")

                val sqlString = "INSERT INTO arts(artname,artistname,year,image) VALUES (?,?,?,?)"
                //Şimdi soru işaretleri ile yukarı daki değerleri birbirine bağlamamız gerekiyor.

                val statement = database.compileStatement(sqlString)
                statement.bindString(1,artName)
                statement.bindString(2,artistName)
                statement.bindString(3,year)
                statement.bindBlob(4,byteArray)

                statement.execute()

            }catch (e : Exception){
                e.printStackTrace()
            }


            val intent = Intent(this@ArtActivity,MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }

    }

    fun makesmallerBitmap(image : Bitmap,maximumSize : Int) : Bitmap {

        var width = image.width
        var height = image.height

        val bitmapRatio : Double = width.toDouble() / height.toDouble()

        if (bitmapRatio > 1){
            width = maximumSize
            val scaledHeight = width / bitmapRatio
            height = scaledHeight.toInt()
        }else{
            height = maximumSize
            val scaledWidth = height * bitmapRatio
            width = scaledWidth.toInt()
        }

        return Bitmap.createScaledBitmap(image,width,height,true)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun selectImage(view : View){

        if (ContextCompat.checkSelfPermission(this,Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_MEDIA_IMAGES)){
                Snackbar.make(view,"Permission needed for gallery",Snackbar.LENGTH_INDEFINITE).setAction("Give Permission",View.OnClickListener {
                    permissionLauncer.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }).show()
            }else{
                permissionLauncer.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }else{
            val intentTOGallery = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intentTOGallery)
        }
    }


    private fun registerLauncher(){

        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result ->

            if (result.resultCode == RESULT_OK){

                val intentFromResult = result.data //geriye intent? veriyor

                if (intentFromResult != null) {
                    val imageData = intentFromResult.data// geriye URİ? veriyor
                    //binding.imageView.setImageURI(imageData) // Direk Uri yi kullanıcıya gösterebilirz fakat bitmap alıp kaydetmemiz lazım
                    if (imageData != null) {
                        try {
                            if (Build.VERSION.SDK_INT >= 28){
                                val source = ImageDecoder.createSource(this@ArtActivity.contentResolver, imageData)
                                selectedBitMap = ImageDecoder.decodeBitmap(source)
                                binding.imageView.setImageBitmap(selectedBitMap)
                            }else{

                                selectedBitMap = MediaStore.Images.Media.getBitmap(this@ArtActivity.contentResolver,imageData)
                                binding.imageView.setImageBitmap(selectedBitMap)
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }

        }


        permissionLauncer = registerForActivityResult(ActivityResultContracts.RequestPermission()){result ->

            if (result){
                //Permission granted
                val intentToGallery = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGallery)
            }else{
                //Permission denied
                Toast.makeText(this@ArtActivity,"Permission needed",Toast.LENGTH_LONG).show()
            }

        }

    }


}