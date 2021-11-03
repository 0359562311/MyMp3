import 'dart:typed_data';

class Song {
  final String filePath;
  final String fileName;
  final String artist;
  final String title;

  Song(this.filePath, this.fileName, this.artist, this.title, this.minutes,
      this.seconds, this.image, this.album);

  final int minutes;
  final int seconds;
  final Uint8List? image;
  final String? album;

  factory Song.fromJson(Map json) {
    List<String> temp = (json['fileName'] as String).split("_");
    String title = json['title'] ??
        (json['fileName'] as String).substring(0, json['fileName'].length - 4);
    String artist = "Không rõ";
    if (temp.length == 3) {
      title = temp[0];
      artist = temp[1];
    }
    int duration = int.parse((json['duration'] as String));
    return Song(
      json['filePath'],
      json['fileName'],
      artist,
      title,
      duration ~/ 60000,
      (duration ~/ 1000) % 60,
      json['image'],
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
        artist.hashCode ^
        title.hashCode ^
        minutes.hashCode ^
        seconds.hashCode ^
        image.hashCode;
  }
}
