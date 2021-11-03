import 'dart:math';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/painting.dart';
import 'package:flutter_mp3/main.dart';
import 'package:flutter_mp3/song_player_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class DragAnimation extends StatefulWidget {
  const DragAnimation({Key? key}) : super(key: key);

  @override
  _DragAnimationState createState() => _DragAnimationState();
}

enum _BottomState { expand, shrink }

class _DragAnimationState extends State<DragAnimation>
    with TickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;
  double _bottomHeight = 90;
  late _BottomState _bottomState;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 250));
    _controller.addListener(() {
      setState(() {
        _bottomHeight = _animation.value;
      });
    });
    _bottomState = _BottomState.shrink;
    context.read(songProvider).init();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _animated(double destination) {
    _animation = _controller
        .drive(Tween<double>(begin: _bottomHeight, end: destination));
    _controller.reset();
    _controller.forward();
  }

  @override
  Widget build(BuildContext context) {
    double width = MediaQuery.of(context).size.width;
    double height = MediaQuery.of(context).size.height;
    double dif = _bottomHeight - 90;
    return Scaffold(
      backgroundColor: Colors.white,
      resizeToAvoidBottomInset: false,
      body: Consumer(
        builder: (context, watch, child) {
          if (watch(songProvider).state == Mp3PlayerState.loading) {
            return const Center(
              child: CircularProgressIndicator(
                color: Colors.purple,
              ),
            );
          } else if (watch(songProvider).state ==
              Mp3PlayerState.permissionNotGranted) {
            return Center(
              child: InkWell(
                onTap: () {
                  context.read(songProvider).promptPermission();
                },
                child: const Padding(
                  padding: EdgeInsets.all(
                    16,
                  ),
                  child: Text(
                    "Hey buddy, I need you to grant some permissons to perform some"
                    " function.\nTouch here!",
                    maxLines: 3,
                  ),
                ),
              ),
            );
          }
          return Stack(
            children: [
              Column(
                children: [
                  const SizedBox(
                    height: 50,
                  ),
                  const Align(
                    alignment: Alignment.center,
                    child: Text(
                      "My Zing Mp3",
                      style: TextStyle(
                          color: Colors.black,
                          fontSize: 18,
                          fontWeight: FontWeight.bold),
                    ),
                  ),
                  SizedBox(
                    height: max(10, 50 - dif / 5),
                  ),
                  Opacity(
                    opacity: max(0, (60 - dif / 3) / 60),
                    child: CircleAvatar(
                      backgroundImage:
                          (watch(songProvider).currentSong?.image != null
                                  ? MemoryImage(
                                      watch(songProvider).currentSong!.image!)
                                  : const AssetImage("assets/mp3.png"))
                              as ImageProvider,
                      backgroundColor: Colors.white,
                      radius: max(0, 90 - dif / 3),
                    ),
                  ),
                  SizedBox(
                    height: max(0, 50 - dif / 5),
                  ),
                  Align(
                    alignment: Alignment(0 - (dif) / (height - 280), 0),
                    child: Text(
                      (watch(songProvider).currentSong?.title ?? "")
                          .toUpperCase(),
                      maxLines: 2,
                      style: const TextStyle(
                          color: Colors.black,
                          fontWeight: FontWeight.bold,
                          fontSize: 24),
                    ),
                  ),
                  const SizedBox(
                    height: 10,
                  ),
                  Align(
                    alignment: Alignment(0 - (dif) / (height - 280), 0),
                    child: Text(
                      watch(songProvider).currentSong?.singer ?? "N/A",
                      style: const TextStyle(color: Colors.black, fontSize: 18),
                    ),
                  ),
                  Slider(
                    min: 0,
                    max: watch(songProvider).currentSong == null
                        ? 0
                        : (watch(songProvider).currentSong!.minutes * 60 +
                                watch(songProvider).currentSong!.seconds)
                            .toDouble(),
                    value: watch(songProvider).currentSong == null
                        ? 0
                        : watch(songProvider).displaySeconds.toDouble(),
                    thumbColor: Colors.purple,
                    activeColor: Colors.purple,
                    inactiveColor: Colors.purple[100],
                    onChanged: (value) {
                      context.read(songProvider).changeDisplay(value.floor());
                    },
                    onChangeStart: (value) {
                      context.read(songProvider).isChanging = true;
                    },
                    onChangeEnd: (value) {
                      context.read(songProvider).isChanging = false;
                      context.read(songProvider).playingSeconds = value.floor();
                      context.read(songProvider).seekTo(value.toInt());
                    },
                  ),
                  Row(
                    children: [
                      const SizedBox(
                        width: 16,
                      ),
                      Text(
                        "${watch(songProvider).displaySeconds ~/ 60}:${_getSecondsString(watch(songProvider).displaySeconds % 60)}",
                        style: const TextStyle(color: Colors.black),
                      ),
                      const Spacer(),
                      Text(
                          "${watch(songProvider).currentSong?.minutes ?? 0}:${watch(songProvider).currentSong == null ? '00' : _getSecondsString(watch(songProvider).currentSong!.seconds)}"),
                      const SizedBox(
                        width: 16,
                      ),
                    ],
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      IconButton(
                          onPressed: () {
                            context.read(songProvider).loop();
                          },
                          icon: Icon(
                            Icons.loop,
                            color: watch(songProvider).isLooping
                                ? Colors.purple
                                : Colors.black,
                          )),
                      IconButton(
                          onPressed: () {
                            context.read(songProvider).previous();
                          },
                          icon: const Icon(Icons.skip_previous)),
                      IconButton(
                          onPressed: () {
                            context.read(songProvider).pauseOrPlay();
                          },
                          icon: watch(songProvider).isPause
                              ? const Icon(Icons.play_arrow)
                              : const Icon(Icons.pause)),
                      IconButton(
                          onPressed: () {
                            context.read(songProvider).next();
                          },
                          icon: const Icon(Icons.skip_next)),
                      IconButton(
                          onPressed: () {
                            context.read(songProvider).random();
                          },
                          icon: Icon(
                            Icons.track_changes,
                            color: watch(songProvider).isRandom
                                ? Colors.purple
                                : Colors.black,
                          )),
                    ],
                  ),
                ],
              ),
              Positioned(
                bottom: 0,
                left: 0,
                height: _bottomHeight,
                width: width,
                child: GestureDetector(
                  onVerticalDragDown: (details) {
                    _controller.stop();
                  },
                  onVerticalDragUpdate: (details) {
                    if ((details.primaryDelta ?? 0) != 0) {
                      setState(() {
                        _bottomHeight = max(
                            90,
                            min(height - 280,
                                _bottomHeight - (details.primaryDelta ?? 0)));
                      });
                    }
                  },
                  onVerticalDragEnd: (details) {
                    if (_bottomState == _BottomState.shrink) {
                      if (_bottomHeight > 140 ||
                          (details.primaryVelocity ?? 0) < -100) {
                        _animated(height - 280);
                        _bottomState = _BottomState.expand;
                      } else {
                        _animated(90);
                      }
                    } else {
                      if ((_bottomHeight > height - 320 &&
                              (details.primaryVelocity ?? 0) > 100) ||
                          (details.primaryVelocity ?? 0) < 0) {
                        _animated(height - 280);
                      } else {
                        _animated(90);
                        _bottomState = _BottomState.shrink;
                      }
                    }
                  },
                  child: Container(
                    height: _bottomHeight,
                    decoration: BoxDecoration(
                        color: Colors.red,
                        gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [Colors.red[400]!, Colors.purple[900]!]),
                        borderRadius: const BorderRadius.only(
                            topLeft: Radius.circular(30),
                            topRight: Radius.circular(30))),
                    child: Column(
                      children: [
                        _bottomState == _BottomState.expand
                            ? const Icon(
                                Icons.arrow_drop_down,
                                color: Colors.white,
                              )
                            : const Icon(
                                Icons.arrow_drop_up,
                                color: Colors.white,
                              ),
                        const SizedBox()
                      ],
                    ),
                  ),
                ),
              )
            ],
          );
        },
      ),
    );
  }

  String _getSecondsString(int seconds) {
    if (seconds >= 10) return seconds.toString();
    return "0" + seconds.toString();
  }
}
