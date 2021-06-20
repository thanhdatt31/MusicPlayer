package com.example.musicplayer.model

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

class Audio(
    val uri: Uri,
    val name: String,
    val artist : String,
    val duration: Int
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readParcelable(Uri::class.java.classLoader)!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readInt()
    ) {
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeParcelable(uri, flags)
        dest?.writeString(name)
        dest?.writeString(artist)
        dest?.writeInt(duration)
    }

    companion object CREATOR : Parcelable.Creator<Audio> {
        override fun createFromParcel(parcel: Parcel): Audio {
            return Audio(parcel)
        }

        override fun newArray(size: Int): Array<Audio?> {
            return arrayOfNulls(size)
        }
    }


}

