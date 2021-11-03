import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'song.dart';

enum Mp3PlayerState { permissionNotGranted, loading, fetchComplete }

class SongProvider extends ChangeNotifier {
  late Mp3PlayerState _state;
  Mp3PlayerState get state => _state;

  final List<Song> _songs = [];
  List<Song> get songs => _songs;
  int _index = -1;

  Song? get currentSong => _index < 0 ? null : _songs[_index];

  int playingSeconds = 0;
  int displaySeconds = 0;
  bool isChanging = false;

  bool _isPause = true;
  bool isLooping = false;
  bool isRandom = false;
  bool get isPause => _isPause;
  late Timer _timer;

  final _methodChannel =
      const MethodChannel("com.tankiem.flutter.flutter_mp3/method_channel");

  StreamSubscription? _streamEventChannel;
  final _eventChannel =
      const EventChannel("com.tankiem.flutter.flutter_mp3/event_channel");

  SongProvider() {
    _state = Mp3PlayerState.loading;
  }

  void init() async {
    _streamEventChannel ??=
        _eventChannel.receiveBroadcastStream().listen(_handleEvent);

    /// first check permision
    final res = await _methodChannel.invokeMethod<bool>("checkPermission");

    /// if permissions are not granted
    if ((res ?? false) == false) {
      _state = Mp3PlayerState.permissionNotGranted;
      notifyListeners();
    } else {
      _state = Mp3PlayerState.loading;
      notifyListeners();
      // get list songs
      _methodChannel.invokeMethod<List>("getSongs");
      _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
        if (!_isPause) {
          playingSeconds = min(playingSeconds + 1,
              _songs[_index].minutes * 60 + _songs[_index].seconds);
          if (!isChanging) {
            displaySeconds = playingSeconds;
          }
          notifyListeners();
        }
      });
    }
  }

  void promptPermission() {
    _methodChannel.invokeMethod("promptPermission");
  }

  void _handleEvent(event) {
    if (event is Map) {
      if (event['action'] == "promptPermission") {
        if (event['data'] == true) {
          init();
        } else {
          _state = Mp3PlayerState.permissionNotGranted;
          notifyListeners();
        }
      } else if (event['action'] == "songChanged") {
        print("TanKiem: songChange in flutter ${event['data']}");
        for (int i = 0; i < _songs.length; i++) {
          if (_songs[i].filePath == event['data']) {
            _index = i;
            break;
          }
        }
        _isPause = false;
        playingSeconds = displaySeconds = 0;
        notifyListeners();
      } else if (event['action'] == "pause") {
        _isPause = event['data'];
        notifyListeners();
      } else if (event['action'] == "stop") {
        _isPause = true;
        notifyListeners();
      } else if (event['action'] == "getSongs") {
        final songsResponse = event['data'];
        for (var i in songsResponse ?? []) {
          if (i['duration'] != null) {
            _songs.add(Song.fromJson(i));
          }
        }
        _getCurrentState();
      }
    }
  }

  void _getCurrentState() async {
    // then get current song if media player is still playing after app has been terminated
    final currentSongResponse =
        await _methodChannel.invokeMethod<Map>("currentState");
    if (currentSongResponse != null) {
      displaySeconds =
          playingSeconds = (currentSongResponse['seconds'] ~/ 1000);
      isLooping = currentSongResponse['isLoop'];
      _isPause = false;
      isRandom = currentSongResponse['isRandom'];
      _index = _songs.indexOf(_songs.firstWhere(
          (element) => element.filePath == currentSongResponse['filePath']));
    }
    _state = Mp3PlayerState.fetchComplete;
    notifyListeners();
    if (_songs.isNotEmpty && _isPause && currentSong == null) start(0);
  }

  void changeDisplay(int value) {
    displaySeconds = value;
    notifyListeners();
  }

  void start(int index) {
    _methodChannel.invokeMethod("playSong", {
      "filePath": _songs[index].filePath,
      "data": _songs.map((e) => e.filePath).toList()
    });
  }

  void next() {
    if (_songs.isNotEmpty) {
      _methodChannel.invokeMethod("next");
    }
  }

  void previous() {
    if (_songs.isNotEmpty) {
      _methodChannel.invokeMethod("previous");
    }
  }

  void loop() async {
    isLooping = !isLooping;
    notifyListeners();
    _methodChannel.invokeMethod("loop", isLooping);
  }

  void random() async {
    isRandom = !isRandom;
    notifyListeners();
    _methodChannel.invokeMethod("random", isRandom);
  }

  void pauseOrPlay() {
    _isPause = !_isPause;
    _methodChannel.invokeMethod(_isPause ? "pause" : "play");
  }

  void seekTo(int value) async {
    await _methodChannel.invokeMethod("seekTo", {
      "seekTo": value * 1000,
    });
  }

  @override
  void dispose() {
    _timer.cancel();
    _streamEventChannel?.cancel();
    if (isPause) {
      _methodChannel.invokeMethod("dispose");
    }
    super.dispose();
  }
}
