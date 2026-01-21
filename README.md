<a name="readme-top"></a>

<!-- PROJECT SHIELDS -->
[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![GPL v3][license-shield]][license-url]

<!-- PROJECT LOGO -->
<br />
<div align="center">
<h3 align="center">LumaLoop</h3>
  <p align="center">
    A Modern, Feature-Rich Live Wallpaper for Android.
    <br />
    <a href="https://github.com/ojitos369/LumaLoop"><strong>Explore the sources »</strong></a>
    <br />
    <br />
    <a href="https://github.com/ojitos369/LumaLoop/issues">Report Bug</a>
    ·
    <a href="https://github.com/ojitos369/LumaLoop/issues">Request Feature</a>
  </p>
</div>

<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#about-the-project">About The Project</a></li>
    <li><a href="#features">Key Features</a></li>
    <li><a href="#technical-architecture">Technical Architecture</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#project-license">Project License</a></li>
  </ol>
</details>

<!-- ABOUT THE PROJECT -->
## About The Project

LumaLoop is a modern, feature-rich Live Wallpaper for Android that transforms your home screen into a dynamic slideshow of high-quality images and videos. Formerly known as *Slideshow Wallpaper*, it has been completely rebuilt with a focus on performance, visual excellence, and advanced media organization.

## Key Features

*   **Mixed Media Support**: Display both high-quality images and videos as your wallpaper.
*   **Intelligent Tagging**: Organize your gallery with a powerful tagging system.
    *   **Auto-tagging**: Automatically assign tags based on file names.
    *   **Logical Filtering**: Filter your wallpaper using AND/OR/XOR logic on tags.
*   **Modern UI**: Fully redesigned interface using **Jetpack Compose** with Material 3.
*   **Customizable Slideshows**:
    *   Adjust transition speed and display duration.
    *   Multiple display modes (Fit, Fill, Scroll).
    *   Sequential or Shuffle playback.
*   **Performance Optimized**:
    *   Utilizes **ExoPlayer** for smooth video playback with optimized buffering.
    *   **OpenGL ES 2.0** for efficient cross-fade transitions and rendering.
    *   Intelligent memory management for high-resolution images.
*   **Media Management**:
    *   Automatic synchronization with the dedicated "LumaLoop" album.
    *   In-app cropping and image editing.
    *   Export/Import tag catalog for backups.

## Technical Architecture

LumaLoop follows a decoupled and reactive architecture using modern Android practices:

*   **View Layer**: 100% **Jetpack Compose** with **StateFlow** for a reactive and fluid UI.
*   **Wallpaper Service**:
    *   `SlideshowWallpaperService`: Core service handling the live wallpaper lifecycle.
    *   `CurrentMediaHandler`: The orchestration layer managing media state, timing, and rendering transitions.
*   **Rendering Engines**:
    *   **ExoPlayer**: Handles video playback and scaling natively.
    *   **GLWallpaperRenderer**: A custom OpenGL renderer using GLSL shaders for image display and cross-fade animations.
*   **Core Libraries**:
    *   **Media3**: Modern player framework for both video and extensible media handling.
    *   **Coil**: Image loading library for the Compose-based gallery.
    *   **Material 3**: The latest design components for a premium look and feel.

## Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Project License

Distributed under GPL v3.

<!-- MARKDOWN LINKS & IMAGES -->
[contributors-shield]: https://img.shields.io/github/contributors/ojitos369/LumaLoop.svg?style=for-the-badge
[contributors-url]: https://github.com/ojitos369/LumaLoop/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/ojitos369/LumaLoop.svg?style=for-the-badge
[forks-url]: https://github.com/ojitos369/LumaLoop/network/members
[stars-shield]: https://img.shields.io/github/stars/ojitos369/LumaLoop.svg?style=for-the-badge
[stars-url]: https://github.com/ojitos369/LumaLoop/stargazers
[issues-shield]: https://img.shields.io/github/issues/ojitos369/LumaLoop.svg?style=for-the-badge
[issues-url]: https://github.com/ojitos369/LumaLoop/issues
[license-shield]: https://img.shields.io/github/license/ojitos369/LumaLoop.svg?style=for-the-badge
[license-url]: https://github.com/ojitos369/LumaLoop/blob/master/LICENSE
