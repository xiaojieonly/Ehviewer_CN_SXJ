#!/usr/bin/env python3
"""
翻译脚本使用示例
"""

import os
import sys

# 添加脚本目录到路径
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from translate_strings_online import OnlineTranslator, StringsTranslator as OnlineStringsTranslator
from translate_strings_deepseek import DeepseekTranslator, StringsTranslator as DeepseekStringsTranslator


def example_google_translation():
    """示例：使用Google翻译"""
    print("=" * 60)
    print("示例1：使用Google翻译")
    print("=" * 60)
    
    # 创建Google翻译器
    translator = OnlineTranslator('google')
    
    # 翻译单个字符串
    text = "Hello World"
    translated = translator.translate_text(text, 'en', 'zh')
    print(f"翻译 '{text}' -> '{translated}'")
    
    # 创建字符串翻译器，优先使用英语源文件
    base_file = "app/src/main/res/values-en/strings.xml"
    if os.path.exists(base_file):
        strings_translator = OnlineStringsTranslator(base_file, translator)
        strings_translator.parse_base_file()
        print(f"基础文件包含 {len(strings_translator.base_strings)} 个字符串")
    else:
        print(f"基础文件不存在: {base_file}")
    
    print()


def example_deepseek_translation():
    """示例：使用Deepseek翻译"""
    print("=" * 60)
    print("示例2：使用Deepseek翻译")
    print("=" * 60)
    
    # 注意：这里需要提供有效的API密钥
    api_key = "your_deepseek_api_key"  # 替换为实际的API密钥
    
    if api_key == "your_deepseek_api_key":
        print("注意：需要提供有效的Deepseek API密钥")
        print("使用方式：python scripts/translate_strings_deepseek.py --base app/src/main/res/values/strings.xml --api-key YOUR_API_KEY")
    else:
        # 创建Deepseek翻译器
        translator = DeepseekTranslator(api_key)
        
        # 翻译单个字符串
        text = "Hello World"
        translated = translator.translate_text(text, '英语', '简体中文')
        print(f"翻译 '{text}' -> '{translated}'")
        
        # 创建字符串翻译器，优先使用英语源文件
        base_file = "app/src/main/res/values-en/strings.xml"
        if os.path.exists(base_file):
            strings_translator = DeepseekStringsTranslator(base_file, translator)
            strings_translator.parse_base_file()
            print(f"基础文件包含 {len(strings_translator.base_strings)} 个字符串")
        else:
            print(f"基础文件不存在: {base_file}")
    
    print()


def example_command_line_usage():
    """示例：命令行使用方式"""
    print("=" * 60)
    print("示例3：命令行使用方式")
    print("=" * 60)
    
    print("1. 使用Google翻译（免费）：")
    print("   python scripts/translate_strings_online.py --base app/src/main/res/values-en/strings.xml --api-type google")
    print()
    
    print("2. 使用Deepseek翻译（需要API密钥）：")
    print("   python scripts/translate_strings_deepseek.py --base app/src/main/res/values-en/strings.xml --api-key YOUR_API_KEY")
    print()
    
    print("3. 使用本地AI翻译（需要安装transformers和torch）：")
    print("   python scripts/translate_strings_local_ai.py --base app/src/main/res/values-en/strings.xml --model Qwen/Qwen2.5-7B-Instruct")
    print()
    
    print("4. 模拟运行（不实际修改文件）：")
    print("   python scripts/translate_strings_online.py --base app/src/main/res/values-en/strings.xml --api-type google --dry-run")
    print()


def main():
    """主函数"""
    print("翻译脚本使用示例")
    print()
    
    # 示例1：Google翻译
    example_google_translation()
    
    # 示例2：Deepseek翻译
    example_deepseek_translation()
    
    # 示例3：命令行使用方式
    example_command_line_usage()
    
    print("=" * 60)
    print("提示：")
    print("1. 首次使用前请确保已安装必要的依赖库")
    print("2. 使用 --dry-run 选项可以预览更改而不实际修改文件")
    print("3. 翻译完成后建议手动检查翻译质量")
    print("=" * 60)


if __name__ == '__main__':
    main()