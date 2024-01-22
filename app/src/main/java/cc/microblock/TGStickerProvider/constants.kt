package cc.microblock.TGStickerProvider

var dPath: String? = "/storage/emulated/0/Documents/TGStickersExported/";
val realDataPath: String get () = "/storage/emulated/0/Android/media/com.tencent.mobileqq/TGStickersExported/v1/";
val destDataPath: String get () = "${dPath}cache/";
val tgseDataPath: String get () = "${dPath}mb_TGSPData/";
val stickerDataPath: String get () =  "${tgseDataPath}sticker/";
val syncFlagsPath: String get () = "${tgseDataPath}stickerSyncFlags/";
val nomediaPath: String get () = "${dPath}.nomedia";