import 'dart:typed_data';

class Song {
  final String filePath;
  final String fileName;
  final String singer;
  final String title;

  Song(this.filePath, this.fileName, this.singer, this.title, this.minutes,
      this.seconds, this.image, this.artist, this.album);

  final int minutes;
  final int seconds;
  final Uint8List? image;
  final String? artist;
  final String? album;

  factory Song.fromJson(Map json) {
    List<String> temp = (json['fileName'] as String).split("_");
    String title = json['title'] ??
        (json['fileName'] as String).substring(0, json['fileName'].length - 4);
    String singer = "Không rõ";
    if (temp.length == 3) {
      title = temp[0];
      singer = temp[1];
    }
    int duration = int.parse((json['duration'] as String));
    return Song(
      json['filePath'],
      json['fileName'],
      singer,
      title,
      duration ~/ 60000,
      (duration ~/ 1000) % 60,
      json['image'],
      json['artist'],
      json['album'],
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;

    return other is Song && other.filePath == filePath;
  }

  @override
  int get hashCode {
    return filePath.hashCode ^
        fileName.hashCode ^
        singer.hashCode ^
        title.hashCode ^
        minutes.hashCode ^
        seconds.hashCode ^
        image.hashCode;
  }
}
