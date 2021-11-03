import 'package:flutter/material.dart';
import 'package:flutter_mp3/main.dart';
import 'package:flutter_mp3/song_player_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class Mp3BottomSheet extends ConsumerWidget {
  final double bottomHeight;
  const Mp3BottomSheet({Key? key, required this.bottomHeight})
      : super(key: key);

  @override
  Widget build(BuildContext context, ScopedReader watch) {
    final data = watch(songProvider);
    if (data.songs.isEmpty) {
      return const Center(child: Text("No mp3 file has been found."));
    }
    if (bottomHeight == 80) {
      return _SongItem(data: data, index: data.index);
    }
    return ListView.builder(
      physics: bottomHeight != MediaQuery.of(context).size.height - 320
          ? const NeverScrollableScrollPhysics()
          : null,
      itemCount: data.songs.length,
      itemBuilder: (context, index) {
        return InkWell(
          onTap: () {
            if (data.index != index) {
              context.read(songProvider).start(index);
            }
          },
          child: _SongItem(
            data: data,
            index: index,
          ),
        );
      },
    );
  }
}

class _SongItem extends StatelessWidget {
  const _SongItem({
    Key? key,
    required this.data,
    required this.index,
  }) : super(key: key);

  final SongProvider data;
  final int index;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      height: 80,
      width: MediaQuery.of(context).size.width,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          data.index == index
              ? const Icon(
                  Icons.circle_rounded,
                  color: Colors.white,
                  size: 8,
                )
              : const SizedBox(
                  width: 8,
                ),
          const SizedBox(
            width: 8,
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  (data.songs[index].title).toUpperCase(),
                  style: const TextStyle(color: Colors.white, fontSize: 18),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(
                  height: 12,
                ),
                Text(
                  data.songs[index].artist,
                  style: const TextStyle(color: Colors.white, fontSize: 12),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          const SizedBox(
            width: 8,
          ),
          const Icon(
            Icons.menu,
            color: Colors.white,
          )
        ],
      ),
    );
  }
}
