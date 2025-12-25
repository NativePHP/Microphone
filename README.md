# Microphone Plugin for NativePHP Mobile

Audio recording plugin for NativePHP Mobile with pause/resume support, background recording, and native permission handling.

## Features

- Record audio in M4A format (AAC codec)
- Pause and resume recordings
- Background recording support (Android foreground service)
- Automatic permission handling
- Custom event dispatching
- Recording status tracking

## Installation

```bash
composer require nativephp/microphone
```

### Register the Plugin

Add the plugin to your `NativePluginsServiceProvider`:

```php
// app/Providers/NativePluginsServiceProvider.php

public function plugins(): array
{
    return [
        \NativePHP\Microphone\MicrophoneServiceProvider::class,
    ];
}
```

## Usage

### Basic Recording

```php
use NativePHP\Microphone\Facades\Microphone;

// Start recording
Microphone::record()->start();

// Stop recording (triggers MicrophoneRecorded event)
Microphone::stop();
```

### With Custom Event

```php
use NativePHP\Microphone\Facades\Microphone;
use App\Events\MyAudioRecordedEvent;

Microphone::record()
    ->event(MyAudioRecordedEvent::class)
    ->start();
```

### With Tracking ID

```php
Microphone::record()
    ->id('voice-note-123')
    ->start();
```

### Pause & Resume

```php
// Pause the current recording
Microphone::pause();

// Resume a paused recording
Microphone::resume();
```

### Check Recording Status

```php
$status = Microphone::getStatus();
// Returns: "idle", "recording", or "paused"
```

### Get Last Recording Path

```php
$path = Microphone::getRecording();
// Returns: "/path/to/audio_123456.m4a" or null
```

## Handling Events

### Using Livewire Attributes

```php
use Livewire\Component;
use Native\Mobile\Attributes\OnNative;
use NativePHP\Microphone\Events\MicrophoneRecorded;
use NativePHP\Microphone\Events\MicrophoneCancelled;

class AudioRecorder extends Component
{
    #[OnNative(MicrophoneRecorded::class)]
    public function handleRecorded($path, $mimeType = null, $id = null)
    {
        // $path - absolute path to the recorded audio file
        // $mimeType - "audio/m4a"
        // $id - the ID you set with ->id(), if any

        // Move the file to storage
        File::move($path, storage_path('app/recordings/audio.m4a'));
    }

    #[OnNative(MicrophoneCancelled::class)]
    public function handleCancelled($cancelled, $reason = null, $id = null)
    {
        // $reason - "permission_denied" or "start_failed"
    }
}
```

### Using Custom Events

```php
// app/Events/MyAudioRecordedEvent.php
namespace App\Events;

class MyAudioRecordedEvent
{
    public function __construct(
        public string $path,
        public string $mimeType = 'audio/m4a',
        public ?string $id = null
    ) {}
}

// In your Livewire component
#[OnNative(MyAudioRecordedEvent::class)]
public function handleMyAudio($path, $mimeType, $id)
{
    // Handle the recording
}
```

## Events

| Event | Properties | Description |
|-------|------------|-------------|
| `MicrophoneRecorded` | `path`, `mimeType`, `id` | Dispatched when recording stops successfully |
| `MicrophoneCancelled` | `cancelled`, `reason`, `id` | Dispatched when recording is cancelled or fails |

### Cancellation Reasons

- `permission_denied` - User denied microphone permission
- `start_failed` - Failed to initialize the recorder

## Permissions

The plugin automatically requests the required permissions:

### Android

- `android.permission.RECORD_AUDIO`

The plugin includes a foreground service for background recording support.

### iOS

- `NSMicrophoneUsageDescription` - Added to Info.plist automatically
- `UIBackgroundModes: audio` - For background recording

## API Reference

### Microphone Facade

| Method | Returns | Description |
|--------|---------|-------------|
| `record()` | `PendingMicrophone` | Start building a recording request |
| `stop()` | `void` | Stop current recording |
| `pause()` | `void` | Pause current recording |
| `resume()` | `void` | Resume paused recording |
| `getStatus()` | `string` | Get status: "idle", "recording", "paused" |
| `getRecording()` | `?string` | Get path to last recording |

### PendingMicrophone

| Method | Returns | Description |
|--------|---------|-------------|
| `id(string $id)` | `self` | Set a tracking ID |
| `getId()` | `string` | Get the ID (auto-generates if not set) |
| `event(string $class)` | `self` | Set custom event class |
| `remember()` | `self` | Flash ID to session |
| `start()` | `bool` | Start recording |

## File Format

Recordings are saved as M4A files with:
- Codec: AAC
- Bit rate: 128 kbps
- Sample rate: 44.1 kHz

Files are initially saved to the app's cache directory and should be moved to permanent storage in your event handler.

## License

MIT